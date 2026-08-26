package zapmc.quicify.quic.zstd;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;
import zapmc.quicify.quic.mux.QuicMux;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZstdStreamCodecTest {

    private static final ZstdParams PARAMS = new ZstdParams(256, true, 3, 17);

    private static final long TEMPLATE_SEED = 20210527L; // easter egg

    private static EmbeddedChannel channel() {
        return channel(PARAMS);
    }

    private static EmbeddedChannel channel(ZstdParams params) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(ZstdStreamCodec.NAME, new ZstdStreamCodec(params));
        return channel;
    }

    private static byte[] roundTrip(EmbeddedChannel sender, EmbeddedChannel receiver, byte[] payload) {
        sender.writeOutbound(Unpooled.wrappedBuffer(payload));
        ByteBuf wire = sender.readOutbound();
        receiver.writeInbound(wire);
        return drain(receiver.readInbound());
    }

    private static int wireSize(EmbeddedChannel sender, EmbeddedChannel receiver, byte[] payload) {
        sender.writeOutbound(Unpooled.wrappedBuffer(payload));
        ByteBuf wire = sender.readOutbound();
        int size = wire.readableBytes();
        receiver.writeInbound(wire);
        ByteBuf decoded = receiver.readInbound();
        assertArrayEquals(payload, drain(decoded));
        return size;
    }

    private static byte[] drain(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    private static byte[] chunkLike(Random random, int length) {
        byte[] out = new byte[length];
        new Random(TEMPLATE_SEED).nextBytes(out);
        for (int i = 0; i < length / 100; i++) {
            out[random.nextInt(length)] = (byte) random.nextInt(256);
        }
        return out;
    }

    private static void release(EmbeddedChannel sender, EmbeddedChannel receiver) {
        sender.finishAndReleaseAll();
        receiver.finishAndReleaseAll();
    }

    private static List<String> userHandlers(EmbeddedChannel channel) {
        return channel.pipeline().names().stream().filter(name -> !name.startsWith("DefaultChannelPipeline$")).toList();
    }

    @Test
    void roundTripsEveryFrame() {
        EmbeddedChannel sender = channel();
        EmbeddedChannel receiver = channel();
        Random random = new Random(1234);
        try {
            for (int i = 0; i < 64; i++) {
                byte[] payload = chunkLike(random, 2048 + random.nextInt(2048));
                assertArrayEquals(payload, roundTrip(sender, receiver, payload));
            }
        } finally {
            release(sender, receiver);
        }
    }

    @Test
    void historyWarmsUpAcrossPackets() {
        EmbeddedChannel sender = channel();
        EmbeddedChannel receiver = channel();
        try {
            Random random = new Random(99);
            byte[] payload = chunkLike(random, 4096);
            int first = wireSize(sender, receiver, payload);
            int last = first;
            for (int i = 0; i < 500; i++) {
                last = wireSize(sender, receiver, chunkLike(new Random(99), 4096));
            }
            assertTrue(last * 20 < first, "compressed size should collapse once the context is warm, was " + first + " then " + last);
        } finally {
            release(sender, receiver);
        }
    }

    @Test
    void keepsSmallPacketsRawWithoutBreakingTheContext() {
        EmbeddedChannel sender = channel();
        EmbeddedChannel receiver = channel();
        try {
            byte[] small = new byte[PARAMS.threshold() - 1];
            new Random(7).nextBytes(small);

            sender.writeOutbound(Unpooled.wrappedBuffer(small));
            ByteBuf wire = sender.readOutbound();
            ByteBuf copy = wire.duplicate();
            int header = VarInt.read(copy);
            assertEquals(1, header & 1, "a packet below the threshold must carry the raw flag");
            assertEquals(small.length, header >>> 1);
            assertEquals(VarInt.getByteSize(header) + small.length, wire.readableBytes(), "a raw frame must cost one varint, the same as vanilla");

            receiver.writeInbound(wire);
            ByteBuf decoded = receiver.readInbound();
            assertArrayEquals(small, drain(decoded));

            byte[] large = chunkLike(new Random(8), 4096);
            assertArrayEquals(large, roundTrip(sender, receiver, large));
        } finally {
            release(sender, receiver);
        }
    }

    @Test
    void waitsForTheWholeFrameBeforeDecoding() {
        EmbeddedChannel sender = channel();
        EmbeddedChannel receiver = channel();
        try {
            byte[] payload = chunkLike(new Random(3), 4096);
            sender.writeOutbound(Unpooled.wrappedBuffer(payload));
            ByteBuf wire = sender.readOutbound();

            int half = wire.readableBytes() / 2;
            receiver.writeInbound(wire.readRetainedSlice(half));
            assertNull(receiver.readInbound(), "a partial frame must not decode");
            receiver.writeInbound(wire);
            assertArrayEquals(payload, drain(receiver.readInbound()));
        } finally {
            release(sender, receiver);
        }
    }

    @Test
    void rejectsAnOversizedDeclaredLength() {
        EmbeddedChannel receiver = channel();
        try {
            ByteBuf frame = Unpooled.buffer();
            ByteBuf body = Unpooled.buffer();
            VarInt.write(body, ZstdStreamCodec.MAX_UNCOMPRESSED_LENGTH + 1);
            body.writeBytes(new byte[8]);
            VarInt.write(frame, body.readableBytes() << 1);
            frame.writeBytes(body);
            assertThrows(DecoderException.class, () -> receiver.writeInbound(frame));
        } finally {
            try {
                receiver.finishAndReleaseAll();
            } catch (DecoderException ignored) {
            }
        }
    }

    @Test
    void rejectsAPayloadThatDoesNotMatchItsDeclaredLength() {
        EmbeddedChannel sender = channel();
        EmbeddedChannel receiver = channel();
        try {
            byte[] payload = chunkLike(new Random(5), 4096);
            sender.writeOutbound(Unpooled.wrappedBuffer(payload));
            ByteBuf wire = sender.readOutbound();

            ByteBuf tampered = Unpooled.buffer();
            VarInt.read(wire);
            int declared = VarInt.read(wire) - 1;
            VarInt.write(tampered, (VarInt.getByteSize(declared) + wire.readableBytes()) << 1);
            VarInt.write(tampered, declared);
            tampered.writeBytes(wire);
            wire.release();

            assertThrows(DecoderException.class, () -> receiver.writeInbound(tampered));
        } finally {
            try {
                release(sender, receiver);
            } catch (DecoderException ignored) {
            }
        }
    }

    @Test
    void installsBehindTheMeterAndAheadOfTheRouter() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst(QuicMux.METER_NAME, new ChannelDuplexHandler());
        channel.pipeline().addAfter(QuicMux.METER_NAME, "quicify_route", new ChannelDuplexHandler());

        QuicCompression.install(channel.pipeline(), PARAMS);

        assertEquals(List.of(QuicMux.METER_NAME, ZstdStreamCodec.NAME, "quicify_route"), userHandlers(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void installsFirstWhenThereIsNoMeter() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst("splitter", new ChannelDuplexHandler());

        QuicCompression.install(channel.pipeline(), PARAMS);

        assertEquals(List.of(ZstdStreamCodec.NAME, "splitter"), userHandlers(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void releasesItsContextsWhenRemoved() {
        EmbeddedChannel channel = channel();
        byte[] payload = chunkLike(new Random(11), 1024);
        channel.writeOutbound(Unpooled.wrappedBuffer(payload));
        ByteBuf wire = channel.readOutbound();
        wire.release();

        channel.pipeline().remove(ZstdStreamCodec.NAME);
        assertNull(channel.pipeline().get(ZstdStreamCodec.NAME));
        channel.finishAndReleaseAll();
    }

    @Test
    void theHighestConfigurableWindowLogIsTheHighestOneAPeerCanDecode() {
        byte[] payload = chunkLike(new Random(27), 64 * 1024);

        EmbeddedChannel sender = channel(new ZstdParams(256, true, 3, 27));
        EmbeddedChannel receiver = channel(new ZstdParams(256, true, 3, 27));
        try {
            assertArrayEquals(payload, roundTrip(sender, receiver, payload), "the highest configurable window log produces frames the peer cannot decode");
        } finally {
            sender.finishAndReleaseAll();
            receiver.finishAndReleaseAll();
        }

        EmbeddedChannel tooWide = channel(new ZstdParams(256, true, 3, 27 + 1));
        EmbeddedChannel peer = channel(PARAMS);
        tooWide.writeOutbound(Unpooled.wrappedBuffer(payload));
        ByteBuf wire = tooWide.readOutbound();
        assertThrows(DecoderException.class, () -> peer.writeInbound(wire), "zstd accepted a window wider than a default decompression context allows, the cap can be raised");

        peer.pipeline().remove(ZstdStreamCodec.NAME);
        tooWide.finishAndReleaseAll();
        peer.finishAndReleaseAll();
    }
}
