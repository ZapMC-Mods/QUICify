package zapmc.quicify;

import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;

public final class QuicifyConfigs {

    private static final QuicifySettings DEFAULTS = new QuicifySettings() {
    };

    private static volatile QuicifySettings settings = DEFAULTS;

    private QuicifyConfigs() {
    }

    public static void install(QuicifySettings implementation) {
        settings = implementation;
    }

    public static boolean enabled() {
        return settings.enabled();
    }

    public static int serverPort() {
        return settings.serverPort();
    }

    public static boolean multiplexing() {
        return settings.multiplexing();
    }

    public static int compressionLevel() {
        return settings.compressionLevel();
    }

    public static int compressionWindowLog() {
        return settings.compressionWindowLog();
    }

    public static QuicCongestionControlAlgorithm congestionControl() {
        return settings.congestionControl();
    }

    public static int connectTimeoutMs() {
        return settings.connectTimeoutMs();
    }

    public static boolean verbose() {
        return settings.verbose();
    }
}
