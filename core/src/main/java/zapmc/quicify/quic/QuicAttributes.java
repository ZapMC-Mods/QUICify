package zapmc.quicify.quic;

import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

public final class QuicAttributes {

    public static final AttributeKey<byte[]> PEER_CERTIFICATE_KEY = AttributeKey.valueOf("quicify:peer_certificate_key");

    public static final AttributeKey<QuicStreamChannel> MASTER_STREAM = AttributeKey.valueOf("quicify:master_stream");

    public static final AttributeKey<Integer> DATAGRAM_CAPACITY = AttributeKey.valueOf("quicify:datagram_capacity");
}
