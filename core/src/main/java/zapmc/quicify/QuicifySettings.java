package zapmc.quicify;

import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;

public interface QuicifySettings {

    default boolean enabled() {
        return true;
    }

    default int serverPort() {
        return 0;
    }

    default boolean multiplexing() {
        return true;
    }

    default int compressionLevel() {
        return 3;
    }

    default int compressionWindowLog() {
        return 18;
    }

    default QuicCongestionControlAlgorithm congestionControl() {
        return QuicCongestionControlAlgorithm.BBR;
    }

    default int connectTimeoutMs() {
        return 3000;
    }

    default boolean verbose() {
        return false;
    }
}
