package zapmc.quicify.quic;

import io.netty.handler.codec.quic.QuicStreamChannel;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Crypt;
import org.jspecify.annotations.NonNull;
import zapmc.quicify.quic.zstd.QuicCompression;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class QuicLoginProbe {

    static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25565;

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        InetSocketAddress address = new InetSocketAddress(host, port);
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> disconnectReason = new AtomicReference<>();

        var future = QuicClientConnector.connectOrFallback(address, connection, () -> {
            throw new IllegalStateException("TCP fallback is not allowed in this probe");
        });
        if (!future.await(15, TimeUnit.SECONDS) || !future.isSuccess()) {
            System.out.println("PROBE FAILED: QUIC connect did not complete: " + future.cause());
            System.exit(1);
        }
        byte[] peerCertificateKey = future.channel().attr(QuicAttributes.PEER_CERTIFICATE_KEY).get();
        if (peerCertificateKey == null) {
            System.out.println("PROBE FAILED: no peer certificate key on the datagram channel");
            System.exit(1);
        }
        QuicStreamChannel master = future.channel().attr(QuicAttributes.MASTER_STREAM).get();
        if (master == null) {
            System.out.println("PROBE FAILED: no master stream on the datagram channel");
            System.exit(1);
        }

        connection.initiateServerboundPlayConnection(
                host, port, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND,
                new ClientLoginPacketListener() {
                    @Override
                    public void handleHello(@NonNull ClientboundHelloPacket packet) {
                        try {
                            byte[] packetKey = rawPublicKey(packet);
                            if (!Arrays.equals(packetKey, peerCertificateKey)) {
                                disconnectReason.set("hello key != TLS peer certificate key");
                                connection.disconnect(Component.literal("cert mismatch"));
                                done.countDown();
                                return;
                            }
                            javax.crypto.SecretKey secretKey = Crypt.generateSecretKey();
                            String digest = QuicLoginCrypto.sessionDigest(packet.getServerId(), packetKey, secretKey.getEncoded());
                            System.out.println("[probe] certificate-bound digest: " + digest);
                            connection.send(QuicLoginCrypto.cleartextKeyPacket(secretKey.getEncoded(), packet.getChallenge()));
                        } catch (Exception e) {
                            disconnectReason.set("hello handling failed: " + e);
                            done.countDown();
                        }
                    }

                    @Override
                    public void handleLoginFinished(@NonNull ClientboundLoginFinishedPacket packet) {
                        disconnectReason.set("unexpected login success (probe is not authenticated!)");
                        done.countDown();
                    }

                    @Override
                    public void handleDisconnect(@NonNull ClientboundLoginDisconnectPacket packet) {
                        disconnectReason.set(packet.reason().toString());
                        done.countDown();
                    }

                    @Override
                    public void handleCompression(@NonNull ClientboundLoginCompressionPacket packet) {
                        QuicCompression.setup(master.parent(), master, packet.getCompressionThreshold(), false);
                    }

                    @Override
                    public void handleCustomQuery(@NonNull ClientboundCustomQueryPacket packet) {
                    }

                    @Override
                    public void handleRequestCookie(@NonNull ClientboundCookieRequestPacket packet) {
                    }

                    @Override
                    public void onDisconnect(@NonNull DisconnectionDetails details) {
                        disconnectReason.compareAndSet(null, "channel closed: " + details.reason().getString());
                        done.countDown();
                    }

                    @Override
                    public boolean isAcceptingMessages() {
                        return connection.isConnected();
                    }

                    @Override
                    public @NonNull PacketFlow flow() {
                        return PacketFlow.CLIENTBOUND;
                    }
                },
                false
        );
        connection.send(new ServerboundHelloPacket("QuicProbe", UUID.randomUUID()));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (done.getCount() > 0 && System.nanoTime() < deadline) {
            if (connection.isConnected()) {
                connection.tick();
            }
            Thread.sleep(50);
        }

        String reason = disconnectReason.get();
        System.out.println("[probe] disconnect reason: " + reason);
        if (reason != null && reason.contains("unverified_username")) {
            System.out.println("PROBE OK: server completed the QUIC auth path and rejected the unauthenticated probe via Mojang hasJoined");
            System.exit(0);
        } else {
            System.out.println("PROBE FAILED: expected unverified_username, got: " + reason);
            System.exit(1);
        }
    }

    private static byte[] rawPublicKey(ClientboundHelloPacket packet) throws Exception {
        Field field = ClientboundHelloPacket.class.getDeclaredField("publicKey");
        field.setAccessible(true);
        return (byte[]) field.get(packet);
    }
}
