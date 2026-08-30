package zapmc.quicify.velocity;

import com.google.common.net.UrlEscapers;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import com.velocitypowered.proxy.crypto.EncryptionUtils;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;

public final class QuicLoginSessionHandler implements MinecraftSessionHandler {

    private static final String HAS_JOINED_URL = System.getProperty("mojang.sessionserver",
            "https://sessionserver.mojang.com/session/minecraft/hasJoined").concat("?username=%s&serverId=%s");

    private static final int AES_KEY_LENGTH = 16;

    private final VelocityServer server;

    private final QuicMinecraftConnection connection;

    private final MinecraftSessionHandler delegate;

    private final LoginInboundConnection inbound;

    private @Nullable String username;

    private QuicLoginSessionHandler(VelocityServer server, QuicMinecraftConnection connection,
                                    MinecraftSessionHandler delegate, LoginInboundConnection inbound) {
        this.server = server;
        this.connection = connection;
        this.delegate = delegate;
        this.inbound = inbound;
    }

    public static MinecraftSessionHandler wrap(VelocityServer server, QuicMinecraftConnection connection,
                                               MinecraftSessionHandler delegate) {
        try {
            LoginInboundConnection inbound = VelocityInternals.loginInboundConnection(delegate);
            return new QuicLoginSessionHandler(server, connection, delegate, inbound);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Quicify.LOGGER.error("QUIC login is not compatible with this Velocity build ({}), closing the connection so the client falls back to TCP", e.toString());
            connection.quicChannel().close();
            return delegate;
        }
    }

    @Override
    public boolean handle(ServerLoginPacket packet) {
        username = packet.getUsername();
        return delegate.handle(packet);
    }

    @Override
    public boolean handle(LoginPluginResponsePacket packet) {
        return delegate.handle(packet);
    }

    public void handle(QuicEncryptionResponsePacket packet) {
        String name = username;
        EncryptionRequestRewriter rewriter = EncryptionRequestRewriter.of(connection.getChannel());
        byte[] challenge = rewriter == null ? null : rewriter.challenge();
        if (name == null || challenge == null) {
            connection.close(true);
            return;
        }
        if (!MessageDigest.isEqual(challenge, packet.challenge())) {
            Quicify.LOGGER.warn("QUIC login challenge did not come back intact, closing the connection");
            connection.close(true);
            return;
        }
        byte[] secret = packet.sharedSecret();
        if (secret.length != AES_KEY_LENGTH) {
            Quicify.LOGGER.warn("QUIC login carried a {} byte shared secret, expected {}", secret.length, AES_KEY_LENGTH);
            connection.close(true);
            return;
        }

        authenticate(name, EncryptionUtils.generateServerId(secret, connection.certificateKey()));
    }

    @SuppressWarnings("resource")
    private void authenticate(String username, String serverId) {
        String playerIp = ((InetSocketAddress) connection.getRemoteAddress()).getHostString();
        String url = String.format(HAS_JOINED_URL, UrlEscapers.urlFormParameterEscaper().escape(username), serverId);
        if (server.getConfiguration().shouldPreventClientProxyConnections()) {
            url += "&ip=" + UrlEscapers.urlFormParameterEscaper().escape(playerIp);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .setHeader("User-Agent", server.getVersion().getName() + "/" + server.getVersion().getVersion())
                .uri(URI.create(url))
                .build();
        HttpClient httpClient = server.createHttpClient();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenCompleteAsync((response, throwable) -> {
                    if (connection.isClosed()) {
                        return;
                    }
                    if (throwable != null) {
                        Quicify.LOGGER.error("Unable to authenticate player over QUIC", throwable);
                        inbound.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
                        return;
                    }
                    if (response.statusCode() == 200) {
                        GameProfile profile = VelocityServer.GENERAL_GSON.fromJson(response.body(), GameProfile.class);
                        succeed(profile, serverId);
                    } else if (response.statusCode() == 204) {
                        inbound.disconnect(Component.translatable("velocity.error.online-mode-only", NamedTextColor.RED));
                    } else {
                        Quicify.LOGGER.error("Got an unexpected error code {} whilst contacting Mojang to log in {} ({})",
                                response.statusCode(), username, playerIp);
                        inbound.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
                    }
                }, connection.eventLoop())
                .whenComplete((_, _) -> httpClient.close());
    }

    private void succeed(GameProfile profile, String serverId) {
        try {
            connection.setActiveSessionHandler(StateRegistry.LOGIN,
                    VelocityInternals.authSessionHandler(server, inbound, profile, true, serverId));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Quicify.LOGGER.error("QUIC login could not hand over to the auth stage", e);
            connection.close(true);
        }
    }

    @Override
    public boolean beforeHandle() {
        return delegate.beforeHandle();
    }

    @Override
    public void handleGeneric(MinecraftPacket packet) {
        delegate.handleGeneric(packet);
    }

    @Override
    public void handleUnknown(ByteBuf buf) {
        delegate.handleUnknown(buf);
    }

    @Override
    public void connected() {
        delegate.connected();
    }

    @Override
    public void disconnected() {
        delegate.disconnected();
    }

    @Override
    public void activated() {
        delegate.activated();
    }

    @Override
    public void deactivated() {
        delegate.deactivated();
    }

    @Override
    public void exception(Throwable throwable) {
        delegate.exception(throwable);
    }

    @Override
    public void writabilityChanged() {
        delegate.writabilityChanged();
    }

    @Override
    public void readCompleted() {
        delegate.readCompleted();
    }
}
