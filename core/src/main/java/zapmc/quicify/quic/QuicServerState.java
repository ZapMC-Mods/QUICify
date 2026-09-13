package zapmc.quicify.quic;

import org.jspecify.annotations.Nullable;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicProtocol;

public final class QuicServerState {

    private static volatile @Nullable QuicAnnouncement announcement;

    public static void publish(int port) {
        announcement = new QuicAnnouncement(QuicProtocol.VERSION, port);
    }

    public static void clear() {
        announcement = null;
    }

    public static @Nullable QuicAnnouncement announcement() {
        return announcement;
    }
}
