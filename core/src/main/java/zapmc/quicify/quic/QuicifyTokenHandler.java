package zapmc.quicify.quic;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicTokenHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public final class QuicifyTokenHandler implements QuicTokenHandler {

    private static final byte[] MAGIC = {'Q', 'C', 'F', 'Y'};

    private static final int TIMESTAMP_LENGTH = Long.BYTES;

    private static final int SIGNATURE_LENGTH = 16;

    private static final int HEADER_LENGTH = MAGIC.length + TIMESTAMP_LENGTH + SIGNATURE_LENGTH;

    private static final long MAX_TOKEN_AGE_MILLIS = 10_000;

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secret;

    private final ThreadLocal<Mac> macs;

    public QuicifyTokenHandler() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.secret = new SecretKeySpec(key, ALGORITHM);
        this.macs = ThreadLocal.withInitial(this::newMac);
    }

    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        InetAddress sender = address.getAddress();
        if (sender == null) {
            return false;
        }
        byte[] connectionId = new byte[dcid.readableBytes()];
        dcid.getBytes(dcid.readerIndex(), connectionId);
        long timestamp = System.currentTimeMillis();

        out.writeBytes(MAGIC);
        out.writeLong(timestamp);
        out.writeBytes(sign(timestamp, sender, connectionId));
        out.writeBytes(connectionId);
        return true;
    }

    @Override
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        InetAddress sender = address.getAddress();
        if (sender == null || token.readableBytes() <= HEADER_LENGTH) {
            return -1;
        }
        int base = token.readerIndex();
        for (int i = 0; i < MAGIC.length; i++) {
            if (token.getByte(base + i) != MAGIC[i]) {
                return -1;
            }
        }

        long timestamp = token.getLong(base + MAGIC.length);
        long age = System.currentTimeMillis() - timestamp;
        if (age < 0 || age > MAX_TOKEN_AGE_MILLIS) {
            return -1;
        }

        byte[] signature = new byte[SIGNATURE_LENGTH];
        token.getBytes(base + MAGIC.length + TIMESTAMP_LENGTH, signature);

        byte[] connectionId = new byte[token.readableBytes() - HEADER_LENGTH];
        token.getBytes(base + HEADER_LENGTH, connectionId);

        if (!MessageDigest.isEqual(signature, sign(timestamp, sender, connectionId))) {
            return -1;
        }
        return HEADER_LENGTH;
    }

    @Override
    public int maxTokenLength() {
        return HEADER_LENGTH + Quic.MAX_CONN_ID_LEN;
    }

    private byte[] sign(long timestamp, InetAddress address, byte[] connectionId) {
        Mac mac = macs.get();
        mac.reset();
        mac.update(MAGIC);
        mac.update(ByteBuffer.allocate(TIMESTAMP_LENGTH).putLong(timestamp).flip());
        mac.update(address.getAddress());
        mac.update(connectionId);
        return Arrays.copyOf(mac.doFinal(), SIGNATURE_LENGTH);
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is unavailable", e);
        }
    }
}
