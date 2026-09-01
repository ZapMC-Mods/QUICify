package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zapmc.quicify.quic.QuicAttributes;
import zapmc.quicify.quic.Varints;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatagramLaneTest {

    private static final int CAPACITY = 1200;

    private EmbeddedChannel parent;

    private QuicChannel quicChannel;

    private StubStream master;

    private List<StubStream> secondaries;

    private MuxStats stats;

    private QuicMuxSession session;

    private List<byte[]> injected;

    private static ByteBuf framed(byte[] body) {
        ByteBuf buf = Unpooled.buffer();
        Varints.write(buf, body.length);
        buf.writeBytes(body);
        return buf;
    }

    private static byte[] body(int size) {
        byte[] body = new byte[size];
        for (int i = 0; i < size; i++) {
            body[i] = (byte) (i + 1);
        }
        return body;
    }

    @BeforeEach
    void setUp() {
        parent = new EmbeddedChannel();
        master = new StubStream(0);
        secondaries = new ArrayList<>();
        injected = new ArrayList<>();

        master.channel.pipeline().addLast("splitter", new ChannelInboundHandlerAdapter());
        master.channel.pipeline().addLast("capture", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ByteBuf buf = (ByteBuf) msg;
                byte[] copy = new byte[buf.readableBytes()];
                buf.readBytes(copy);
                injected.add(copy);
                buf.release();
            }
        });

        quicChannel = MuxStubs.quicChannel(parent);
        stats = new MuxStats(null);
        session = new QuicMuxSession(quicChannel, master.handle, false, stats, "splitter");
        quicChannel.attr(MuxStats.KEY).set(stats);
        quicChannel.attr(QuicMuxSession.KEY).set(session);
        quicChannel.attr(QuicAttributes.DATAGRAM_CAPACITY).set(CAPACITY);
    }

    @AfterEach
    void tearDown() {
        for (StubStream secondary : secondaries) {
            secondary.channel.finishAndReleaseAll();
        }
        master.channel.finishAndReleaseAll();
        parent.finishAndReleaseAll();
    }

    private void activate() {
        session.arm();
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            StubStream secondary = new StubStream(4L * (secondaries.size() + 1));
            secondaries.add(secondary);
            session.registerSecondary(PacketCategory.bySecondaryIndex(i), secondary.handle);
        }
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.markReady(PacketCategory.bySecondaryIndex(i));
        }
        assertEquals("ACTIVE", session.stateName());
    }

    private void crossABarrier() {
        session.beginBarrier(false);
        session.finishBarrier();
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }
    }

    private ByteBuf datagram(int epoch, byte[] body) {
        ByteBuf buf = Unpooled.buffer(1 + body.length);
        buf.writeByte(epoch);
        buf.writeBytes(body);
        return buf;
    }

    private ChannelPromise promise() {
        return master.channel.newPromise();
    }

    @Test
    void anEligiblePacketLeavesAsOneDatagramStampedWithTheGeneration() {
        activate();

        byte[] body = body(24);
        ByteBuf framed = framed(body);
        ChannelPromise promise = promise();

        assertTrue(session.routeDatagram(framed, promise));
        session.flushDatagrams();

        assertEquals(0, framed.refCnt(), "the framed buffer leaked after being turned into a datagram");
        assertTrue(promise.isSuccess());
        assertEquals(1L, stats.datagramTx());
        assertTrue(master.channel.outboundMessages().isEmpty(), "a datagram went down the master stream as well");

        ByteBuf sent = parent.readOutbound();
        assertNotNull(sent, "nothing was written on the QuicChannel");
        assertEquals(1 + body.length, sent.readableBytes(), "the length varint was not stripped");
        assertEquals(0, sent.readUnsignedByte());
        byte[] payload = new byte[sent.readableBytes()];
        sent.readBytes(payload);
        sent.release();
        assertArrayEquals(body, payload);
    }

    @Test
    void withoutTheAdvertisedExtensionThePacketStaysOnItsStream() {
        activate();
        quicChannel.attr(QuicAttributes.DATAGRAM_CAPACITY).set(null);

        ByteBuf framed = framed(body(24));
        assertFalse(session.routeDatagram(framed, promise()));
        assertEquals(1, framed.refCnt(), "the framed buffer was consumed even though no datagram was sent");
        framed.release();
        assertNull(parent.readOutbound());
    }

    @Test
    void aPayloadOverTheAdvertisedCapacityStaysOnItsStream() {
        activate();

        ByteBuf framed = framed(body(CAPACITY));
        assertFalse(session.routeDatagram(framed, promise()));
        assertEquals(1, framed.refCnt());
        framed.release();
        assertNull(parent.readOutbound());
        assertEquals(0L, stats.datagramTx());
    }

    @Test
    void onlyAnActiveSessionSendsDatagrams() {
        session.arm();
        assertEquals("ARMED", session.stateName());

        ByteBuf framed = framed(body(24));
        assertFalse(session.routeDatagram(framed, promise()));
        framed.release();

        session.disable();
        ByteBuf afterDisable = framed(body(24));
        assertFalse(session.routeDatagram(afterDisable, promise()));
        afterDisable.release();
        assertNull(parent.readOutbound());
    }

    @Test
    void aReceivedDatagramIsInjectedBehindTheMasterSplitter() {
        activate();

        byte[] body = body(32);
        assertTrue(DatagramLane.deliver(quicChannel, datagram(0, body)));

        assertEquals(1, injected.size(), "the datagram never reached the handler behind the splitter");
        assertArrayEquals(body, injected.getFirst());
        assertEquals(1L, stats.datagramRx());
        assertEquals(0L, stats.datagramDropped());
    }

    @Test
    void aBarrierAdvancesTheGenerationAndStaleDatagramsAreRejected() {
        activate();
        assertEquals(0, session.generation());

        crossABarrier();
        assertEquals(1, session.generation(), "a barrier did not open a new datagram generation");
        activate();

        ByteBuf stale = datagram(0, body(32));
        assertFalse(DatagramLane.deliver(quicChannel, stale), "a datagram from the previous generation was decoded");
        assertEquals(0, stale.refCnt());
        assertTrue(injected.isEmpty());
        assertEquals(1L, stats.datagramDropped());

        assertTrue(DatagramLane.deliver(quicChannel, datagram(1, body(32))));
        assertEquals(1, injected.size());
    }

    @Test
    void aDatagramArrivingOutsideAnActiveSessionIsDroppedInsteadOfDecoded() {
        session.arm();

        ByteBuf early = datagram(0, body(32));
        assertFalse(DatagramLane.deliver(quicChannel, early));
        assertEquals(0, early.refCnt(), "a rejected datagram leaked");
        assertTrue(injected.isEmpty());
        assertEquals(1L, stats.datagramDropped());
    }
}
