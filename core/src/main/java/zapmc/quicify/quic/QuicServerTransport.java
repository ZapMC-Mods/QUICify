package zapmc.quicify.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.Quicify;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.mux.DatagramLane;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class QuicServerTransport {

    private final QuicCertManager certificates;

    private final Set<Channel> connections = ConcurrentHashMap.newKeySet();

    private volatile EventLoopGroup group;

    private volatile Channel datagramChannel;

    private volatile boolean draining;

    public QuicServerTransport(QuicCertManager certificates) {
        this.certificates = certificates;
    }

    public synchronized void start(InetSocketAddress address, ChannelHandler streamHandler) throws Exception {
        start(address, streamHandler, QuicDatagramTransport.NIO);
    }

    public synchronized void start(InetSocketAddress address, ChannelHandler streamHandler, QuicDatagramTransport transport) throws Exception {
        stop();
        draining = false;

        QuicSslContext sslContext = QuicSslContextBuilder
                .forServer(certificates.privateKey(), null, certificates.certificate())
                .applicationProtocols(QuicProtocol.ALPN_OFFER)
                .build();

        ChannelHandler codec = QuicTuning.applyTo(new QuicServerCodecBuilder())
                .sslContext(sslContext)
                .maxIdleTimeout(30, TimeUnit.SECONDS)
                .initialMaxData(24_000_000)
                .initialMaxStreamDataBidirectionalLocal(5_000_000)
                .initialMaxStreamDataBidirectionalRemote(5_000_000)
                .initialMaxStreamsBidirectional(QuicProtocol.MAX_CONCURRENT_STREAMS)
                .tokenHandler(new QuicifyTokenHandler())
                .handler(new ConnectionTracker())
                .streamHandler(streamHandler)
                .build();

        group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("quicify-server", true), transport.ioHandlerFactory());
        datagramChannel = QuicTuning.applyTo(new Bootstrap(), 4 * 1024 * 1024)
                .group(group)
                .channel(transport.channelClass())
                .handler(codec)
                .bind(address)
                .sync()
                .channel();
    }

    @SuppressWarnings("resource")
    public synchronized void drain(long graceMillis) {
        Channel channel = datagramChannel;
        if (channel == null) {
            return;
        }
        if (!draining) {
            draining = true;
            EventLoopGroup boundGroup = group;
            channel.closeFuture().addListener(_ -> boundGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS));

            if (connections.isEmpty()) {
                channel.close();
                return;
            }
            Quicify.LOGGER.debug("QUIC listener draining, {} connection(s) still open", connections.size());
        }
        if (graceMillis > 0) {
            channel.eventLoop().schedule(() -> {
                channel.close();
            }, graceMillis, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        draining = false;
        connections.clear();
        if (datagramChannel != null) {
            datagramChannel.close().awaitUninterruptibly();
            datagramChannel = null;
        }
        if (group != null) {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).awaitUninterruptibly();
            group = null;
        }
    }

    public synchronized InetSocketAddress localAddress() {
        return datagramChannel == null ? null : (InetSocketAddress) datagramChannel.localAddress();
    }

    @ChannelHandler.Sharable
    private final class ConnectionTracker extends ChannelInboundHandlerAdapter {

        private static final io.netty.util.AttributeKey<java.net.SocketAddress> REMOTE = io.netty.util.AttributeKey.valueOf("quicify:remote");

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            ctx.pipeline().addAfter(ctx.name(), DatagramLane.NAME, new DatagramLane());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().attr(REMOTE).set(ctx.channel().remoteAddress());
            if (draining) {
                ctx.close();
                return;
            }
            connections.add(ctx.channel());
            Quicify.LOGGER.debug("QUIC connection established with {}", ctx.channel().attr(REMOTE).get());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connections.remove(ctx.channel());
            Quicify.LOGGER.debug("QUIC connection closed with {}", ctx.channel().attr(REMOTE).get());

            Channel channel = datagramChannel;
            if (draining && connections.isEmpty() && channel != null) {
                channel.close();
            }
        }
    }
}
