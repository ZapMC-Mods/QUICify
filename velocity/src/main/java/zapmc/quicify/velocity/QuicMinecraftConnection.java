package zapmc.quicify.velocity;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.InitialLoginSessionHandler;
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import zapmc.quicify.Quicify;
import zapmc.quicify.quic.zstd.QuicCompression;

import java.net.SocketAddress;
import java.security.PublicKey;

public class QuicMinecraftConnection extends MinecraftConnection {

    private final VelocityServer server;

    private final QuicChannel quicChannel;

    private final PublicKey certificateKey;

    private final SocketAddress remoteAddress;

    private boolean muxInstalled;

    public QuicMinecraftConnection(Channel channel, VelocityServer server, QuicChannel quicChannel, PublicKey certificateKey) {
        super(channel, server);
        this.server = server;
        this.quicChannel = quicChannel;
        this.certificateKey = certificateKey;
        this.remoteAddress = quicChannel.remoteSocketAddress();
    }

    public QuicChannel quicChannel() {
        return quicChannel;
    }

    public PublicKey certificateKey() {
        return certificateKey;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public void enableEncryption(byte[] secret) {
    }

    @Override
    public void setCompressionThreshold(int threshold) {
        QuicCompression.setup(quicChannel, getChannel(), threshold, true);
    }

    @Override
    public void setActiveSessionHandler(StateRegistry registry, MinecraftSessionHandler sessionHandler) {
        if (sessionHandler instanceof InitialLoginSessionHandler) {
            sessionHandler = QuicLoginSessionHandler.wrap(server, this, sessionHandler);
        }
        super.setActiveSessionHandler(registry, sessionHandler);
        if (registry == StateRegistry.LOGIN && !muxInstalled) {
            muxInstalled = true;
            try {
                VelocityMux.install(quicChannel, (QuicStreamChannel) getChannel(), this);
            } catch (Throwable t) {
                Quicify.LOGGER.warn("QUIC multiplexing could not be set up ({}), staying single-stream", t.toString());
            }
        }
    }
}
