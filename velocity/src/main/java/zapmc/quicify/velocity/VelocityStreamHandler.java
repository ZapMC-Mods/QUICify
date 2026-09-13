package zapmc.quicify.velocity;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.HandshakeSessionHandler;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.network.limiter.PacketLimiter;
import com.velocitypowered.proxy.network.limiter.SimpleBytesPerSecondLimiter;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import zapmc.quicify.Quicify;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.mux.MuxStreams;

import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public final class VelocityStreamHandler extends ChannelInboundHandlerAdapter {

    /**
     * Holds the master connection's {@link PacketLimiter} (if {@code packets-per-second} or
     * {@code packet-limiter-bytes} is enabled) so {@link VelocityMux} can account secondary-stream
     * traffic against the very same limiter once multiplexing is negotiated.
     */
    static final AttributeKey<PacketLimiter> PACKET_LIMITER = AttributeKey.valueOf("quicify:packet_limiter");

    private final VelocityServer server;

    private final QuicCertManager certificates;

    public VelocityStreamHandler(VelocityServer server, QuicCertManager certificates) {
        this.server = server;
        this.certificates = certificates;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        QuicChannel quicChannel = ctx.channel().parent() instanceof QuicChannel parent ? parent : null;
        if (quicChannel == null) {
            ctx.close();
            return;
        }

        if (ctx.channel() instanceof QuicStreamChannel stream && stream.streamId() != 0) {
            MuxStreams.acceptSecondary(quicChannel, stream);
            return;
        }

        try {
            install(ctx.pipeline(), quicChannel);
        } catch (Throwable t) {
            Quicify.LOGGER.error("Failed to set up a QUIC connection, closing it", t);
            quicChannel.close();
        }
    }

    private void install(ChannelPipeline pipeline, QuicChannel quicChannel) {
        VelocityConfiguration configuration = server.getConfiguration();

        pipeline.addLast(Connections.FRAME_DECODER, new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.SERVERBOUND))
                .addLast(Connections.READ_TIMEOUT, new ReadTimeoutHandler(configuration.getReadTimeout(), TimeUnit.MILLISECONDS))
                .addLast(Connections.FRAME_ENCODER, new QuicVarintLengthEncoder())
                .addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND))
                .addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));

        VelocityConfiguration.PacketLimiterConfig limiter = configuration.getPacketLimiterConfig();
        if (limiter.interval() > 0 && (limiter.bytes() > 0 || limiter.pps() > 0)) {
            PacketLimiter packetLimiter = new SimpleBytesPerSecondLimiter(limiter.pps(), limiter.bytes(), limiter.interval());
            pipeline.get(MinecraftVarintFrameDecoder.class).setPacketLimiter(packetLimiter);
            pipeline.channel().attr(PACKET_LIMITER).set(packetLimiter);
        }

        PublicKey certificateKey = certificates.certificate().getPublicKey();
        EncryptionRequestRewriter.install(pipeline.channel(), certificateKey);

        MinecraftConnection connection = new QuicMinecraftConnection(pipeline.channel(), server, quicChannel, certificateKey);
        pipeline.addAfter(Connections.FRAME_DECODER, QuicEncryptionResponseDecoder.NAME, new QuicEncryptionResponseDecoder(connection));
        connection.setActiveSessionHandler(StateRegistry.HANDSHAKE, new HandshakeSessionHandler(connection, server));
        pipeline.addLast(Connections.HANDLER, connection);
        StatusAnnouncer.install(pipeline.channel());

        pipeline.channel().closeFuture().addListener(_ -> quicChannel.close());
    }
}
