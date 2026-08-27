package zapmc.quicify.quic;

import org.jspecify.annotations.Nullable;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicifyConfig;

import java.net.InetSocketAddress;

public record QuicConnectDecision(boolean attemptQuic, InetSocketAddress target) {

    public static QuicConnectDecision resolve(boolean hasPingResult, @Nullable QuicAnnouncement announcement, InetSocketAddress serverAddress, QuicifyConfig.ConnectMode connectMode) {
        if (hasPingResult && announcement == null && connectMode != QuicifyConfig.ConnectMode.FORCE_QUIC) {
            return new QuicConnectDecision(false, serverAddress);
        }
        if (announcement == null || announcement.port() == serverAddress.getPort()) {
            return new QuicConnectDecision(true, serverAddress);
        }
        return new QuicConnectDecision(true, withPort(serverAddress, announcement.port()));
    }

    private static InetSocketAddress withPort(InetSocketAddress address, int port) {
        return address.getAddress() == null ? InetSocketAddress.createUnresolved(address.getHostString(), port) : new InetSocketAddress(address.getAddress(), port);
    }
}
