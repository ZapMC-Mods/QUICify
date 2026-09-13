package zapmc.quicify.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuicVarintLengthEncoderTest {

    @Test
    void framesAPacketIntoASingleBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel(new QuicVarintLengthEncoder());
        try {
            channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{0x2A, 1, 2, 3}));

            ByteBuf framed = channel.readOutbound();
            assertEquals(5, framed.readableBytes());
            assertEquals(4, framed.readByte(), "the frame opens with its length");
            assertEquals(0x2A, framed.readByte(), "the packet id follows it in the same buffer");
            assertNull(channel.readOutbound(), "one packet has to be one write, or the router splits it");
            framed.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void framesAPacketNeedingAMultiByteLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new QuicVarintLengthEncoder());
        try {
            byte[] payload = new byte[300];
            payload[0] = 0x2A;
            channel.writeOutbound(Unpooled.wrappedBuffer(payload));

            ByteBuf framed = channel.readOutbound();
            assertEquals(302, framed.readableBytes());
            assertEquals(0x2A, FrameRouting.peekId(framed, true));
            assertNull(channel.readOutbound());
            framed.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
