package zapmc.quicify.quic;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import zapmc.quicify.Quicify;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.mux.QuicMux;

@ChannelHandler.Sharable
public final class ServerStreamHandler extends ChannelInboundHandlerAdapter {

    private final ServerConnectionListener listener;

    private final QuicCertManager certificates;

    public ServerStreamHandler(ServerConnectionListener listener, QuicCertManager certificates) {
        this.listener = listener;
        this.certificates = certificates;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        QuicChannel quicChannel = ctx.channel().parent() instanceof QuicChannel parent ? parent : null;

        if (quicChannel != null && ctx.channel() instanceof QuicStreamChannel stream && stream.streamId() != 0) {
            QuicMux.acceptSecondary(quicChannel, stream);
            return;
        }

        MinecraftServer server = listener.getServer();
        ChannelPipeline pipeline = ctx.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
        Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);

        int rateLimit = server.getRateLimitPacketsPerSecond();
        Connection connection = rateLimit > 0 ? new RateKickingConnection(rateLimit) : new Connection(PacketFlow.SERVERBOUND);
        if (quicChannel != null) {
            ctx.channel().closeFuture().addListener(_ -> quicChannel.close());
            if (connection instanceof QuicifyConnection quic) {
                quic.quicify$markQuicServer(quicChannel, certificates.certificate().getPublicKey());
            }
        }
        listener.getConnections().add(connection);
        connection.configurePacketHandler(pipeline);
        if (quicChannel != null && ctx.channel() instanceof QuicStreamChannel master) {
            try {
                QuicMux.install(quicChannel, master, false, null);
            } catch (Throwable t) {
                Quicify.LOGGER.warn("QUIC multiplexing could not be set up ({}), staying single-stream", t.toString());
            }
        }
        connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(server, connection));
    }
}
