package zapmc.quicify.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import zapmc.quicify.QuicProtocol;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class QuicClientTransport {

    private static final byte[] PING_PAYLOAD = "PING".getBytes(StandardCharsets.US_ASCII);

    public static CompletableFuture<Duration> ping(InetSocketAddress address, Duration timeout) {
        CompletableFuture<Duration> result = new CompletableFuture<>();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("quicify-ping", true), NioIoHandler.newFactory());
        Channel datagramChannel;
        try {
            QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols(QuicProtocol.ALPN)
                    .build();

            ChannelHandler codec = QuicTuning.applyTo(new QuicClientCodecBuilder())
                    .sslContext(sslContext)
                    .maxIdleTimeout(30, TimeUnit.SECONDS)
                    .initialMaxData(10_000_000)
                    .initialMaxStreamDataBidirectionalLocal(5_000_000)
                    .initialMaxStreamDataBidirectionalRemote(5_000_000)
                    .build();

            datagramChannel = QuicTuning.applyTo(new Bootstrap(), 1024 * 1024)
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0)
                    .sync()
                    .channel();
        } catch (Exception e) {
            group.shutdownGracefully();
            result.completeExceptionally(e);
            return result;
        }

        QuicChannel.newBootstrap(datagramChannel)
                .remoteAddress(address)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        result.completeExceptionally(cause);
                    }
                })
                .connect()
                .addListener(connectFuture -> {
                    if (!connectFuture.isSuccess()) {
                        result.completeExceptionally(connectFuture.cause());
                        return;
                    }
                    QuicChannel quicChannel = (QuicChannel) connectFuture.getNow();
                    long startNanos = System.nanoTime();
                    quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ctx.writeAndFlush(Unpooled.wrappedBuffer(PING_PAYLOAD));
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ((ByteBuf) msg).release();
                            result.complete(Duration.ofNanos(System.nanoTime() - startNanos));
                            ctx.close();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            result.completeExceptionally(cause);
                            ctx.close();
                        }
                    }).addListener(streamFuture -> {
                        if (!streamFuture.isSuccess()) {
                            result.completeExceptionally(streamFuture.cause());
                        }
                    });
                });

        result.whenComplete((_, _) -> {
            datagramChannel.close();
            group.shutdownGracefully();
        });
        result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return result;
    }
}
