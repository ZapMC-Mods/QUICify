package zapmc.quicify.quic;

import io.netty.handler.codec.quic.QuicChannel;
import net.minecraft.network.BandwidthDebugMonitor;
import org.jspecify.annotations.Nullable;

import java.security.PublicKey;

public interface QuicifyConnection {

    boolean quicify$isQuic();

    @Nullable
    QuicChannel quicify$quicChannel();

    void quicify$markQuicServer(QuicChannel channel, PublicKey localCertificateKey);

    void quicify$markQuicClient(QuicChannel channel, byte[] peerCertificateKey);

    @Nullable
    PublicKey quicify$localCertificateKey();

    byte @Nullable [] quicify$peerCertificateKey();

    @Nullable
    BandwidthDebugMonitor quicify$bandwidthDebugMonitor();
}
