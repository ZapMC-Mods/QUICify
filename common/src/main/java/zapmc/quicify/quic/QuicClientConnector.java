package zapmc.quicify.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfig;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.QuicifyFzzyConfigs;
import zapmc.quicify.quic.mux.DatagramLane;
import zapmc.quicify.quic.mux.QuicMux;
import zapmc.quicify.quic.zstd.ZstdAvailability;

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class QuicClientConnector {

    private static volatile EventLoopGroup group;

    private static QuicDatagramTransport groupTransport = QuicDatagramTransport.NIO;

    private static int boundChannels;

    public static ChannelFuture connectOrFallback(InetSocketAddress address, Connection connection, Supplier<ChannelFuture> vanillaFallback) {
        return connectOrFallback(address, connection, QuicDatagramTransport.NIO, vanillaFallback);
    }

    public static ChannelFuture connectOrFallback(InetSocketAddress address, Connection connection, QuicDatagramTransport transport, Supplier<ChannelFuture> vanillaFallback) {
        if (!QuicifyConfigs.enabled() || QuicifyFzzyConfigs.connectMode() == QuicifyConfig.ConnectMode.FORCE_TCP) {
            return vanillaFallback.get();
        }

        if (!QuicAvailability.check() || !ZstdAvailability.check()) {
            if (QuicifyFzzyConfigs.connectMode() == QuicifyConfig.ConnectMode.FORCE_QUIC) {
                throw describedFailure("QUIC or zstd native library unavailable and connectMode is FORCE_QUIC", null);
            }
            return vanillaFallback.get();
        }

        if (QuicifyFzzyConfigs.connectMode() != QuicifyConfig.ConnectMode.FORCE_QUIC && QuicBackoff.INSTANCE.isCoolingDown(address)) {
            if (QuicifyConfigs.verbose()) {
                Quicify.LOGGER.info("QUIC to {} failed recently, going straight to TCP", address);
            }
            return vanillaFallback.get();
        }

        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC attempt to {} (mode {}, timeout {} ms)", address, QuicifyFzzyConfigs.connectMode(), QuicifyConfigs.connectTimeoutMs());
        }

        Channel datagramChannel;
        try {
            datagramChannel = bindDatagramChannel(transport);
        } catch (Throwable t) {
            Quicify.LOGGER.warn("QUIC connection to {} failed ({}), falling back to TCP", address, t.toString());
            if (QuicifyFzzyConfigs.connectMode() == QuicifyConfig.ConnectMode.FORCE_QUIC) {
                throw describedFailure("QUIC connection to " + address + " failed and connectMode is FORCE_QUIC", t);
            }
            return vanillaFallback.get();
        }

        ChannelPromise promise = datagramChannel.newPromise();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<ChannelFuture> activeFallback = new AtomicReference<>();

        Thread attemptThread = new Thread(() -> runQuicAttempt(datagramChannel, address, connection, promise, vanillaFallback, cancelled, activeFallback), "quicify-connect-attempt");
        attemptThread.setDaemon(true);

        promise.addListener(f -> {
            if (f.isCancelled()) {
                cancelled.set(true);
                attemptThread.interrupt();
                ChannelFuture fallback = activeFallback.get();
                if (fallback != null) {
                    fallback.cancel(true);
                    if (fallback.isSuccess()) {
                        fallback.channel().close();
                    }
                }
                datagramChannel.close();
            }
        });

        attemptThread.start();
        return promise;
    }

    private static Channel bindDatagramChannel(QuicDatagramTransport transport) throws Exception {
        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicProtocol.ALPN_OFFER)
                .build();

        ChannelHandler codec = QuicTuning.applyTo(new QuicClientCodecBuilder())
                .sslContext(sslContext)
                .maxIdleTimeout(30, TimeUnit.SECONDS)
                .initialMaxData(24_000_000)
                .initialMaxStreamDataBidirectionalLocal(5_000_000)
                .initialMaxStreamDataBidirectionalRemote(5_000_000)
                .build();

        GroupLease lease = acquireGroup(transport);
        try {
            Channel channel = QuicTuning.applyTo(new Bootstrap(), 1024 * 1024)
                    .group(lease.group())
                    .channel(lease.transport().channelClass())
                    .handler(codec)
                    .bind(0)
                    .sync()
                    .channel();
            channel.closeFuture().addListener(_ -> releaseGroup());
            return channel;
        } catch (Throwable t) {
            releaseGroup();
            throw t;
        }
    }

    private static void runQuicAttempt(Channel datagramChannel, InetSocketAddress address, Connection connection, ChannelPromise promise, Supplier<ChannelFuture> vanillaFallback, AtomicBoolean cancelled, AtomicReference<ChannelFuture> activeFallback) {
        long handshakeTimeoutMs = QuicifyConfigs.connectTimeoutMs();
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(handshakeTimeoutMs);
        AtomicBoolean pipelineInstalled = new AtomicBoolean(false);
        try {
            if (cancelled.get()) {
                datagramChannel.close();
                return;
            }

            QuicChannel quicChannel = QuicChannel.newBootstrap(datagramChannel)
                    .remoteAddress(address)
                    .handler(new DatagramLane())
                    .connect()
                    .get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);

            if (QuicifyConfigs.verbose()) {
                Quicify.LOGGER.info("QUIC handshake with {} completed in {} ms", address, (System.nanoTime() - startNanos) / 1_000_000L);
            }

            byte[] peerCertificateKey = peerCertificateKey(quicChannel);

            CountDownLatch activated = new CountDownLatch(1);
            QuicStreamChannel streamChannel = quicChannel
                    .createStream(QuicStreamType.BIDIRECTIONAL, new ClientStreamHandler(connection, activated, pipelineInstalled))
                    .get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);

            if (!activated.await(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("stream did not activate in time");
            }

            if (cancelled.get()) {
                datagramChannel.close();
                return;
            }

            if (connection instanceof QuicifyConnection quic) {
                quic.quicify$markQuicClient(quicChannel, peerCertificateKey);
            }
            datagramChannel.attr(QuicAttributes.PEER_CERTIFICATE_KEY).set(peerCertificateKey);
            datagramChannel.attr(QuicAttributes.MASTER_STREAM).set(streamChannel);

            streamChannel.closeFuture().addListener(_ -> quicChannel.close());
            quicChannel.closeFuture().addListener(_ -> datagramChannel.close());

            QuicBackoff.INSTANCE.recordSuccess(address);
            if (!promise.trySuccess()) {
                datagramChannel.close();
                return;
            }
            Quicify.LOGGER.info("Connected to {} over QUIC", address);
        } catch (Throwable t) {
            if (cancelled.get()) {
                datagramChannel.close();
                return;
            }

            QuicBackoff.INSTANCE.recordFailure(address);
            Quicify.LOGGER.warn("QUIC connection to {} failed ({}), falling back to TCP", address, t.toString());
            datagramChannel.close().awaitUninterruptibly();

            if (QuicifyFzzyConfigs.connectMode() == QuicifyConfig.ConnectMode.FORCE_QUIC) {
                promise.tryFailure(describedFailure("QUIC connection to " + address + " failed and connectMode is FORCE_QUIC", t));
                return;
            }

            if (pipelineInstalled.get()) {
                promise.tryFailure(describedFailure("QUIC connection to " + address + " failed after the packet pipeline was installed, so it cannot be retried over TCP on the same connection", t));
                return;
            }

            try {
                if (QuicifyConfigs.verbose()) {
                    Quicify.LOGGER.warn("Giving up on QUIC for {}, starting the TCP fallback connect", address);
                }
                ChannelFuture fallback = vanillaFallback.get();
                activeFallback.set(fallback);
                if (cancelled.get()) {
                    fallback.cancel(true);
                    if (fallback.isSuccess()) {
                        fallback.channel().close();
                    }
                    promise.tryFailure(new CancellationException("QUIC connection attempt cancelled"));
                    return;
                }
                fallback.addListener(f -> {
                    if (f.isSuccess()) {
                        promise.trySuccess();
                    } else if (f.isCancelled()) {
                        promise.cancel(true);
                    } else {
                        Throwable cause = f.cause();
                        promise.tryFailure(cause != null && cause.getMessage() != null ? cause : describedFailure("TCP fallback connect to " + address + " failed", cause));
                    }
                });
            } catch (Throwable fallbackError) {
                promise.tryFailure(describedFailure("TCP fallback connect to " + address + " failed", fallbackError));
            }
        }
    }

    private static long remainingMillis(long deadlineNanos) {
        return Math.max(0L, (deadlineNanos - System.nanoTime()) / 1_000_000L);
    }

    private static byte[] peerCertificateKey(QuicChannel quicChannel) throws Exception {
        javax.net.ssl.SSLEngine sslEngine = quicChannel.sslEngine();
        if (sslEngine == null) {
            throw new IllegalStateException("QUIC channel has no SSL engine");
        }
        java.security.cert.Certificate[] chain = sslEngine.getSession().getPeerCertificates();
        if (chain.length == 0 || !(chain[0] instanceof java.security.cert.X509Certificate leaf)) {
            throw new IllegalStateException("server presented no TLS certificate");
        }
        return leaf.getPublicKey().getEncoded();
    }

    private static RuntimeException describedFailure(String message, Throwable original) {
        RuntimeException failure = new RuntimeException(original == null ? message : message + ": " + original);
        if (original != null) {
            failure.addSuppressed(original);
        }
        return failure;
    }

    private static synchronized GroupLease acquireGroup(QuicDatagramTransport transport) {
        if (group == null) {
            groupTransport = transport;
            group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("quicify-client", true), transport.ioHandlerFactory());
        }
        boundChannels++;
        return new GroupLease(group, groupTransport);
    }

    private static synchronized void releaseGroup() {
        if (--boundChannels > 0) {
            return;
        }
        boundChannels = 0;
        if (group != null) {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            group = null;
        }
    }

    private record GroupLease(EventLoopGroup group, QuicDatagramTransport transport) {
    }

    private static final class ClientStreamHandler extends ChannelInboundHandlerAdapter {

        private final Connection connection;

        private final CountDownLatch activated;

        private final AtomicBoolean pipelineInstalled;

        ClientStreamHandler(Connection connection, CountDownLatch activated, AtomicBoolean pipelineInstalled) {
            this.connection = connection;
            this.activated = activated;
            this.pipelineInstalled = pipelineInstalled;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            BandwidthDebugMonitor monitor = connection instanceof QuicifyConnection duck ? duck.quicify$bandwidthDebugMonitor() : null;
            ChannelPipeline pipeline = ctx.pipeline();
            Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, monitor);
            connection.configurePacketHandler(pipeline);
            pipelineInstalled.set(true);
            if (ctx.channel().parent() instanceof QuicChannel quicChannel && ctx.channel() instanceof QuicStreamChannel master) {
                try {
                    QuicMux.install(quicChannel, master, true, monitor);
                } catch (Throwable t) {
                    Quicify.LOGGER.warn("QUIC multiplexing could not be set up ({}), staying single-stream", t.toString());
                }
            }
            pipeline.addLast("quicify_ready", new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    super.channelActive(ctx);
                    activated.countDown();
                }
            });
        }
    }
}
