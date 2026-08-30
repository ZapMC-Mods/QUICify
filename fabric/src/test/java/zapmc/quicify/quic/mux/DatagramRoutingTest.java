package zapmc.quicify.quic.mux;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatagramRoutingTest {

    private static final Set<PacketType<?>> EXPECTED = Set.of(
            GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES,
            GamePacketTypes.CLIENTBOUND_SOUND,
            GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
            GamePacketTypes.CLIENTBOUND_LEVEL_EVENT,
            GamePacketTypes.CLIENTBOUND_SET_TIME,
            GamePacketTypes.CLIENTBOUND_ANIMATE,
            GamePacketTypes.CLIENTBOUND_HURT_ANIMATION,
            GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactlyTheExpectedTypesTravelOnDatagrams() {
        List<String> unexpected = new ArrayList<>();
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, _) -> {
            if (PacketRouting.isDatagram(type) != EXPECTED.contains(type)) {
                unexpected.add(type.toString());
            }
        });
        assertTrue(unexpected.isEmpty(), "the datagram set drifted from what this test pins: " + unexpected);
    }

    @Test
    void nothingServerboundTravelsOnDatagrams() {
        List<String> serverbound = new ArrayList<>();
        GameProtocols.SERVERBOUND_TEMPLATE.details().listPackets((type, _) -> {
            if (PacketRouting.isDatagram(type)) {
                serverbound.add(type.toString());
            }
        });
        assertTrue(serverbound.isEmpty(), "a serverbound packet was made datagram-eligible: " + serverbound);
    }

    @Test
    void everyDatagramTypeIsCosmeticAndNeverACheckpoint() {
        for (PacketType<?> type : EXPECTED) {
            PacketCategory category = PacketRouting.categoryOf(type);
            assertTrue(category == PacketCategory.AMBIENT || category == PacketCategory.REALTIME, type + " must stay on AMBIENT or REALTIME, was " + category);
            assertFalse(PacketRouting.isBarrier(type), type + " is a barrier and cannot be droppable");
            assertFalse(PacketRouting.isPlayEntry(type), type + " is a play entry and cannot be droppable");
            assertFalse(PacketRouting.isTerminal(type), type + " is a terminal and cannot be droppable");
        }
    }

    @Test
    void stopSoundStaysOnTheReliableStream() {
        assertFalse(PacketRouting.isDatagram(GamePacketTypes.CLIENTBOUND_STOP_SOUND), "a lost stop_sound would leave a looping sound playing forever");
        assertEquals(PacketCategory.AMBIENT, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_STOP_SOUND));
    }
}
