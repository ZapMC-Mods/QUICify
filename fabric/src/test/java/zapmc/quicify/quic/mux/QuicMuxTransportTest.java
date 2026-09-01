package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.quic.QuicAttributes;
import zapmc.quicify.quic.QuicClientConnector;
import zapmc.quicify.quic.QuicServerTransport;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicMuxTransportTest {

    @TempDir
    Path configDir;

    @Test
    void clientOpensOneStreamPerCategoryAndClosingOneKeepsTheConnection() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        StreamCollector collector = new StreamCollector();
        QuicServerTransport server = new QuicServerTransport(QuicCertManager.loadOrGenerate(configDir));
        try {
            server.start(new InetSocketAddress("127.0.0.1", 0), collector);
            InetSocketAddress address = server.localAddress();
            assertNotNull(address);

            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                Connection connection = new Connection(PacketFlow.CLIENTBOUND);
                ChannelFuture future = QuicClientConnector.connectOrFallback(address, connection, () -> {
                    throw new IllegalStateException("TCP fallback must not trigger");
                });
                assertTrue(future.await(10, TimeUnit.SECONDS), "QUIC connection promise did not complete in time");
                assertTrue(future.isSuccess(), "expected QUIC connection to succeed: " + future.cause());

                assertTrue(collector.helloReceived.await(10, TimeUnit.SECONDS), "expected one secondary stream per category, saw " + collector.categories);
                assertEquals(Set.of(PacketCategory.REALTIME, PacketCategory.UI, PacketCategory.AMBIENT, PacketCategory.WORLD), collector.categories);
                assertEquals(QuicProtocol.ALPN, collector.alpn.get(), "QUICify ALPN was not negotiated");

                Channel datagramChannel = future.channel();
                QuicStreamChannel master = datagramChannel.attr(QuicAttributes.MASTER_STREAM).get();
                assertNotNull(master);
                assertEquals(QuicProtocol.ALPN, collector.alpn.get(), "QUICify ALPN was not negotiated");
                assertNotNull(master.parent().attr(QuicAttributes.DATAGRAM_CAPACITY).get(), "the client never saw the QUIC DATAGRAM extension, so the datagram lane can never open");
                assertNotNull(collector.datagramCapacity.get(), "the server never saw the QUIC DATAGRAM extension, so the datagram lane can never open");

                QuicStreamChannel secondary = collector.streams.getFirst();
                secondary.close().syncUninterruptibly();

                assertFalse(datagramChannel.closeFuture().await(1, TimeUnit.SECONDS), "closing a secondary stream tore down the whole QUIC connection");
                assertTrue(datagramChannel.isOpen());

                connection.disconnect(Component.literal("test disconnect"));
                assertTrue(datagramChannel.closeFuture().await(10, TimeUnit.SECONDS), "datagram channel leaked open after Connection.disconnect()");
            });
        } finally {
            server.stop();
        }
    }

    @ChannelHandler.Sharable
    private static final class StreamCollector extends ChannelInboundHandlerAdapter {

        private final CountDownLatch helloReceived = new CountDownLatch(PacketCategory.SECONDARY_COUNT);

        private final Set<PacketCategory> categories = ConcurrentHashMap.newKeySet();

        private final CopyOnWriteArrayList<QuicStreamChannel> streams = new CopyOnWriteArrayList<>();

        private final AtomicReference<String> alpn = new AtomicReference<>();

        private final AtomicReference<Integer> datagramCapacity = new AtomicReference<>();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            if (ctx.channel().parent() instanceof QuicChannel parent) {
                SSLEngine engine = parent.sslEngine();
                if (engine != null) {
                    alpn.compareAndSet(null, engine.getApplicationProtocol());
                }
                datagramCapacity.compareAndSet(null, parent.attr(QuicAttributes.DATAGRAM_CAPACITY).get());
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf) || !(ctx.channel() instanceof QuicStreamChannel stream)) {
                return;
            }
            try {
                if (stream.streamId() != 0 && buf.isReadable()) {
                    PacketCategory category = PacketCategory.byWireId(buf.readByte());
                    if (category != null && categories.add(category)) {
                        streams.add(stream);
                        helloReceived.countDown();
                    }
                }
            } finally {
                buf.release();
            }
        }
    }
}
