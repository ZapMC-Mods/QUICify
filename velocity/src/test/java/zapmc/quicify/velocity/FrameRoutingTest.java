package zapmc.quicify.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameRoutingTest {

    private static ByteBuf buffer(int... bytes) {
        ByteBuf buf = Unpooled.buffer(bytes.length);
        for (int value : bytes) {
            buf.writeByte(value);
        }
        return buf;
    }

    private static void writeVarint(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    @Test
    void readsTheIdOfAStrippedFrame() {
        ByteBuf frame = buffer(0x2A, 0x01, 0x02, 0x03);
        assertEquals(0x2A, FrameRouting.peekId(frame, false));
        assertEquals(4, frame.readableBytes(), "peeking must not consume the frame");
    }

    @Test
    void skipsTheLengthPrefixOfAFramedPacket() {
        ByteBuf frame = Unpooled.buffer();
        writeVarint(frame, 4);
        writeVarint(frame, 0x2A);
        frame.writeByte(1).writeByte(2).writeByte(3);
        assertEquals(0x2A, FrameRouting.peekId(frame, true));
    }

    @Test
    void readsAMultiByteId() {
        ByteBuf frame = Unpooled.buffer();
        writeVarint(frame, 300);
        assertEquals(300, FrameRouting.peekId(frame, false));
    }

    @Test
    void readsAnIdBehindAMultiByteLength() {
        ByteBuf frame = Unpooled.buffer();
        writeVarint(frame, 1000);
        writeVarint(frame, 130);
        assertEquals(130, FrameRouting.peekId(frame, true));
    }

    @Test
    void reportsNoIdForATruncatedFrame() {
        assertEquals(-1, FrameRouting.peekId(Unpooled.buffer(), false));
        assertEquals(-1, FrameRouting.peekId(buffer(0x80), false));
        assertEquals(-1, FrameRouting.peekId(buffer(0x04), true));
        assertEquals(-1, FrameRouting.peekId(buffer(0x80, 0x80, 0x80, 0x80, 0x80, 0x01), false));
    }
}
