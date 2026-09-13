package zapmc.quicify.quic;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public final class Varints {

    public static final int MAX_VARINT_SIZE = 5;

    private static final int SEGMENT_BITS = 0x7F;

    private static final int CONTINUATION_BIT = 0x80;

    private Varints() {
    }

    public static boolean hasContinuationBit(byte value) {
        return (value & CONTINUATION_BIT) == CONTINUATION_BIT;
    }

    public static int getByteSize(int value) {
        for (int size = 1; size < MAX_VARINT_SIZE; size++) {
            if ((value & -1 << size * 7) == 0) {
                return size;
            }
        }
        return MAX_VARINT_SIZE;
    }

    public static int read(ByteBuf in) {
        int value = 0;
        int shift = 0;
        byte read;
        do {
            read = in.readByte();
            value |= (read & SEGMENT_BITS) << shift;
            shift += 7;
            if (shift > 35) {
                throw new DecoderException("VarInt too big");
            }
        } while (hasContinuationBit(read));
        return value;
    }

    public static void write(ByteBuf out, int value) {
        while ((value & -SEGMENT_BITS - 1) != 0) {
            out.writeByte(value & SEGMENT_BITS | CONTINUATION_BIT);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
