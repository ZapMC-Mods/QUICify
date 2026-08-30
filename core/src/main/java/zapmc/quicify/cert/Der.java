package zapmc.quicify.cert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class Der {

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    static byte[] tlv(int tag, byte[] content) {
        byte[] length = length(content.length);
        byte[] out = new byte[1 + length.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(content, 0, out, 1 + length.length, content.length);
        return out;
    }

    private static byte[] length(int n) {
        if (n < 0x80) {
            return new byte[]{(byte) n};
        }
        int bytes = 0;
        for (int v = n; v != 0; v >>>= 8) {
            bytes++;
        }
        byte[] out = new byte[1 + bytes];
        out[0] = (byte) (0x80 | bytes);
        for (int i = bytes; i >= 1; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>>= 8;
        }
        return out;
    }

    static byte[] sequence(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    static byte[] explicit0(byte[] content) {
        return tlv(0xA0, content);
    }

    static byte[] integer(byte... magnitude) {
        int i = 0;
        while (i < magnitude.length - 1 && magnitude[i] == 0) {
            i++;
        }
        byte[] trimmed = new byte[magnitude.length - i];
        System.arraycopy(magnitude, i, trimmed, 0, trimmed.length);
        if ((trimmed[0] & 0x80) != 0) {
            trimmed = concat(new byte[]{0}, trimmed);
        }
        return tlv(0x02, trimmed);
    }

    static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        List<Byte> bytes = new ArrayList<>();
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        bytes.add((byte) (first * 40 + second));
        for (int i = 2; i < parts.length; i++) {
            long value = Long.parseLong(parts[i]);
            long[] stack = new long[10];
            int depth = 0;
            stack[depth++] = value & 0x7F;
            value >>>= 7;
            while (value != 0) {
                stack[depth++] = 0x80 | (value & 0x7F);
                value >>>= 7;
            }
            while (depth > 0) {
                bytes.add((byte) stack[--depth]);
            }
        }
        byte[] content = new byte[bytes.size()];
        for (int i = 0; i < content.length; i++) {
            content[i] = bytes.get(i);
        }
        return tlv(0x06, content);
    }

    static byte[] bitString(byte[] data) {
        return tlv(0x03, concat(new byte[]{0}, data));
    }

    static byte[] utf8String(String value) {
        return tlv(0x0C, value.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] utcTime(String value) {
        return tlv(0x17, value.getBytes(StandardCharsets.US_ASCII));
    }
}
