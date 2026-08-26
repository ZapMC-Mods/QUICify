package zapmc.quicify.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zapmc.quicify.cert.QuicCertManager;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicTransportTest {

    @TempDir
    Path configDir;

    @Test
    void pingPongOverLoopback() throws Exception {
        QuicServerTransport server = new QuicServerTransport(QuicCertManager.loadOrGenerate(configDir));
        try {
            server.start(new InetSocketAddress("127.0.0.1", 0), new EchoHandler());
            InetSocketAddress address = server.localAddress();
            assertNotNull(address);
            assertTrue(address.getPort() > 0);

            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                Duration rtt = QuicClientTransport.ping(new InetSocketAddress("127.0.0.1", address.getPort()), Duration.ofSeconds(10)).join();
                assertNotNull(rtt);
            });
        } finally {
            server.stop();
        }
    }

    @Test
    void connectorCapturesPeerCertificateKey() throws Exception {
        QuicCertManager certificates = QuicCertManager.loadOrGenerate(configDir);
        QuicServerTransport server = new QuicServerTransport(certificates);
        try {
            server.start(new InetSocketAddress("127.0.0.1", 0), new EchoHandler());
            InetSocketAddress address = server.localAddress();

            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                Connection connection = new Connection(PacketFlow.CLIENTBOUND);
                ChannelFuture future = QuicClientConnector.connectOrFallback(address, connection, () -> {
                    throw new IllegalStateException("TCP fallback must not trigger");
                });
                assertTrue(future.await(10, TimeUnit.SECONDS), "QUIC connection promise did not complete in time");
                assertTrue(future.isSuccess(), "expected QUIC connection to succeed: " + future.cause());
                byte[] peerKey = future.channel().attr(QuicAttributes.PEER_CERTIFICATE_KEY).get();
                assertArrayEquals(certificates.certificate().getPublicKey().getEncoded(), peerKey);
                future.channel().close().syncUninterruptibly();
            });
        } finally {
            server.stop();
        }
    }

    @Test
    void disconnectClosesQuicChannelAndDatagramChannel() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        QuicServerTransport server = new QuicServerTransport(QuicCertManager.loadOrGenerate(configDir));
        try {
            server.start(new InetSocketAddress("127.0.0.1", 0), new EchoHandler());
            InetSocketAddress address = server.localAddress();

            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                Connection connection = new Connection(PacketFlow.CLIENTBOUND);
                ChannelFuture future = QuicClientConnector.connectOrFallback(address, connection, () -> {
                    throw new IllegalStateException("TCP fallback must not trigger");
                });
                assertTrue(future.await(10, TimeUnit.SECONDS), "QUIC connection promise did not complete in time");
                assertTrue(future.isSuccess(), "expected QUIC connection to succeed: " + future.cause());

                Channel datagramChannel = future.channel();
                assertTrue(datagramChannel.isOpen(), "datagram channel should be open right after connecting");

                connection.disconnect(Component.literal("test disconnect"));

                assertTrue(datagramChannel.closeFuture().await(10, TimeUnit.SECONDS), "datagram/UDP channel leaked open after Connection.disconnect()");
                assertFalse(datagramChannel.isOpen(), "datagram/UDP channel leaked open after Connection.disconnect()");
            });
        } finally {
            server.stop();
        }
    }

    @ChannelHandler.Sharable
    private static final class EchoHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.writeAndFlush(msg);
        }
    }
}
