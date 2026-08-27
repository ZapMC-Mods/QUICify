package zapmc.quicify.quic;

import io.netty.handler.codec.quic.Quic;
import zapmc.quicify.Quicify;

public final class QuicAvailability {

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
            Quic.ensureAvailability();
            Quicify.LOGGER.info("QUIC support available (quiche native library loaded)");
            available = true;
        } catch (Throwable t) {
            Quicify.LOGGER.error("QUIC native library unavailable on this platform, staying on TCP", t);
            available = false;
        }
        return available;
    }
}
