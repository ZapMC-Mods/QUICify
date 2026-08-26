package zapmc.quicify.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.QuicAvailability;
import zapmc.quicify.quic.QuicDatagramTransport;
import zapmc.quicify.quic.QuicServerState;
import zapmc.quicify.quic.QuicServerTransport;
import zapmc.quicify.quic.ServerStreamHandler;
import zapmc.quicify.quic.zstd.ZstdAvailability;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {

    @Unique
    private QuicServerTransport quicify$transport;
    @Unique
    private QuicCertManager quicify$certificates;

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "startTcpServerListener", at = @At("RETURN"))
    private void quicify$startQuicListener(InetAddress address, int port, CallbackInfo ci) {
        if (!QuicifyConfigs.enabled() || !QuicAvailability.check() || !ZstdAvailability.check()) {
            return;
        }
        int configuredPort = QuicifyConfigs.serverPort();
        int quicPort = configuredPort == 0 ? port : configuredPort;
        try {
            if (quicify$transport == null) {
                quicify$certificates = QuicCertManager.loadOrGenerate(FabricLoader.getInstance().getConfigDir().resolve("quicify"));
                quicify$transport = new QuicServerTransport(quicify$certificates);
            }
            QuicDatagramTransport datagramTransport = QuicDatagramTransport.select(getServer().useNativeTransport());
            quicify$transport.start(new InetSocketAddress(address, quicPort), new ServerStreamHandler((ServerConnectionListener) (Object) this, quicify$certificates), datagramTransport);
            QuicServerState.publish(quicPort);
            Quicify.LOGGER.info("QUIC listener started on port {} (UDP, {} transport)", quicPort, datagramTransport);
            if (QuicifyConfigs.verbose()) {
                Quicify.LOGGER.info("QUIC listener bound to {}", quicify$transport.localAddress());
            }
        } catch (Throwable t) {
            Quicify.LOGGER.error("Failed to start QUIC listener, staying TCP-only", t);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void quicify$stopQuicListener(CallbackInfo ci) {
        quicify$drainQuicListener(3000);
    }

    @Inject(method = "stopTcpServerListener", at = @At("HEAD"))
    private void quicify$stopQuicListenerOnUnpublish(CallbackInfo ci) {
        quicify$drainQuicListener(0);
    }

    @Unique
    private void quicify$drainQuicListener(long graceMillis) {
        QuicServerState.clear();
        if (quicify$transport != null) {
            quicify$transport.drain(graceMillis);
        }
    }
}
