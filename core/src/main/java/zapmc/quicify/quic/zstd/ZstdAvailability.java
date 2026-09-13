package zapmc.quicify.quic.zstd;

import com.github.luben.zstd.Zstd;
import zapmc.quicify.Quicify;

public final class ZstdAvailability {

    private static volatile Boolean available;

    public static boolean check() {
        Boolean cached = available;
        return cached != null ? cached : probe();
    }

    private static synchronized boolean probe() {
        if (available != null) {
            return available;
        }
        try {
            Zstd.compressBound(1L);
            Quicify.LOGGER.info("zstd support available (zstd-jni native library loaded)");
            available = true;
        } catch (Throwable t) {
            Quicify.LOGGER.error("zstd native library unavailable on this platform, staying on TCP", t);
            available = false;
        }
        return available;
    }
}
