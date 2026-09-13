package zapmc.quicify.quic.mux;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BarrierGateTest {

    private EmbeddedChannel parent;

    private StubStream master;

    private List<StubStream> secondaries;

    private QuicMuxSession session;

    private MuxStats stats;

    private EmbeddedChannel gate;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Packet<?> packet(PacketType<?> type) {
        return new StubPacket(type);
    }

    @BeforeEach
    void setUp() {
        parent = new EmbeddedChannel();
        master = new StubStream(0);
        secondaries = new ArrayList<>();
        stats = new MuxStats(null);
        session = new QuicMuxSession(MuxStubs.quicChannel(parent), master.handle, false, stats, "splitter");
        session.arm();
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            StubStream secondary = new StubStream(4L * (i + 1));
            secondaries.add(secondary);
            session.registerSecondary(PacketCategory.bySecondaryIndex(i), secondary.handle);
        }
        gate = new EmbeddedChannel(new BarrierGate(session, PacketRouting::barrierOf));
    }

    @AfterEach
    void tearDown() {
        gate.finishAndReleaseAll();
        for (StubStream secondary : secondaries) {
            secondary.channel.finishAndReleaseAll();
        }
        master.channel.finishAndReleaseAll();
        parent.finishAndReleaseAll();
    }

    @Test
    void aSecondBarrierHeldBehindTheFirstIsStillProcessedAsABarrier() {
        Packet<?> startConfiguration = packet(GamePacketTypes.CLIENTBOUND_START_CONFIGURATION);
        Packet<?> respawn = packet(GamePacketTypes.CLIENTBOUND_RESPAWN);

        gate.writeInbound(startConfiguration);
        assertEquals("DRAINING", session.stateName());

        gate.writeInbound("before");
        gate.writeInbound(respawn);
        gate.writeInbound("after");
        assertNull(gate.readInbound(), "the gate delivered a packet while the drain was still pending");

        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }

        assertSame(startConfiguration, gate.readInbound());
        assertEquals("before", gate.readInbound());
        assertSame(respawn, gate.readInbound());
        assertEquals("after", gate.readInbound());
        assertNull(gate.readInbound());

        assertEquals("ARMED", session.stateName(), "the second barrier was delivered without going through the mux barrier handling");
    }

    @Test
    void aPacketInjectedFromASecondaryDuringTheHoldGoesThroughAheadOfTheBarrier() {
        Packet<?> startConfiguration = packet(GamePacketTypes.CLIENTBOUND_START_CONFIGURATION);
        Packet<?> inFlight = packet(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT);

        gate.writeInbound(startConfiguration);
        assertEquals("DRAINING", session.stateName());

        stats.beginInjection();
        try {
            gate.writeInbound(inFlight);
        } finally {
            stats.endInjection();
        }

        assertSame(inFlight, gate.readInbound(), "a packet still in flight on a secondary was held behind the barrier that is waiting for it");

        gate.writeInbound("after");
        assertNull(gate.readInbound(), "a master packet written behind the barrier was delivered before it");

        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }

        assertSame(startConfiguration, gate.readInbound());
        assertEquals("after", gate.readInbound());
        assertNull(gate.readInbound());
    }

    @Test
    void aBarrierThatNeedsNoDrainIsDeliveredStraightAway() {
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            secondaries.get(i).channel.close().syncUninterruptibly();
        }

        Packet<?> login = packet(GamePacketTypes.CLIENTBOUND_LOGIN);
        gate.writeInbound(login);

        assertSame(login, gate.readInbound());
        assertEquals("ARMED", session.stateName());
    }

    private record StubPacket(PacketType<?> packetType) implements Packet<PacketListener> {

        @Override
        @SuppressWarnings("unchecked")
        public PacketType<? extends Packet<PacketListener>> type() {
            return (PacketType<? extends Packet<PacketListener>>) packetType;
        }

        @Override
        public void handle(PacketListener listener) {
        }
    }
}
