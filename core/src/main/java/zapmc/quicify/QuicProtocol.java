package zapmc.quicify;

public final class QuicProtocol {

    public static final String ALPN = "quicify/1";

    public static final String[] ALPN_OFFER = {ALPN};

    public static final int MAX_CONCURRENT_STREAMS = 12;

    public static final String STATUS_FIELD = "quicify";

    public static final int VERSION = 1;
}
