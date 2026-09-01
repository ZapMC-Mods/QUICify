package zapmc.quicify.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ListenerType;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.QuicAvailability;
import zapmc.quicify.quic.QuicDatagramTransport;
import zapmc.quicify.quic.QuicServerState;
import zapmc.quicify.quic.QuicServerTransport;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.QuicMuxSession;
import zapmc.quicify.quic.zstd.QuicCompression;
import zapmc.quicify.quic.zstd.ZstdAvailability;
import zapmc.quicify.quic.zstd.ZstdParams;

import java.net.InetSocketAddress;
import java.nio.file.Path;

@Plugin(
        id = Quicify.MOD_ID,
        name = "QUICify",
        version = BuildConstants.VERSION,
        description = "Blazing fast QUIC transport for Minecraft networking",
        url = "https://github.com/ZapMC-Mods/QUICify",
        authors = {"ZapMC"}
)
public final class QuicifyVelocity {

    private final ProxyServer proxy;

    private final Path dataDirectory;

    private QuicCertManager certificates;

    private QuicServerTransport transport;

    private boolean usable;

    @Inject
    public QuicifyVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Quicify.init(dataDirectory);
        QuicifyConfigs.install(QuicifyVelocityConfig.load(dataDirectory));

        if (!QuicifyConfigs.enabled()) {
            return;
        }
        QuicNativeIsolation.prepare();
        if (!QuicAvailability.check() || !ZstdAvailability.check()) {
            Quicify.LOGGER.warn("QUIC or zstd natives are unavailable on this platform, staying TCP-only");
            return;
        }
        if (!(proxy instanceof VelocityServer server)) {
            Quicify.LOGGER.warn("Unexpected proxy implementation {}, staying TCP-only", proxy.getClass().getName());
            return;
        }

        try {
            certificates = QuicCertManager.loadOrGenerate(dataDirectory);
            transport = new QuicServerTransport(certificates);
            QuicifyServerChannelInitializer.install(server, VelocityInternals.connectionManager(server));
            usable = true;
        } catch (Throwable t) {
            Quicify.LOGGER.error("Failed to initialize QUICify, staying TCP-only", t);
        }
    }

    @Subscribe
    public void onListenerBound(ListenerBoundEvent event) {
        if (!usable || event.getListenerType() != ListenerType.MINECRAFT) {
            return;
        }
        VelocityServer server = (VelocityServer) proxy;
        int configuredPort = QuicifyConfigs.serverPort();
        int quicPort = configuredPort == 0 ? event.getAddress().getPort() : configuredPort;
        try {
            QuicDatagramTransport datagramTransport = QuicDatagramTransport.select(true);
            transport.start(new InetSocketAddress(event.getAddress().getAddress(), quicPort),
                    new VelocityStreamHandler(server, certificates), datagramTransport);
            QuicServerState.publish(quicPort);
            Quicify.LOGGER.info("QUIC listener started on port {} (UDP, {} transport)", quicPort, datagramTransport);
            if (QuicifyConfigs.verbose()) {
                Quicify.LOGGER.info("QUIC listener bound to {}", transport.localAddress());
            }
        } catch (Throwable t) {
            Quicify.LOGGER.error("Failed to start QUIC listener, staying TCP-only", t);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (!(event.getPlayer() instanceof ConnectedPlayer player) || !(player.getConnection() instanceof QuicMinecraftConnection connection)) {
            return;
        }
        QuicMuxSession session = QuicMuxSession.of(connection.quicChannel());
        ZstdParams compression = QuicCompression.params(connection.quicChannel());
        Quicify.LOGGER.info("{} is connected over QUIC ({}, {})", player, session == null || session.disabled() ? "single stream" : PacketCategory.values().length + " streams", compression == null ? "uncompressed" : "zstd level " + compression.level());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        QuicServerState.clear();
        if (transport != null) {
            transport.drain(3000);
            transport.stop();
        }
    }
}
