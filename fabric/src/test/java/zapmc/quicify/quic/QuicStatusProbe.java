package zapmc.quicify.quic;

import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.Bootstrap;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class QuicStatusProbe {
    static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25565;

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        InetSocketAddress address = new InetSocketAddress(host, port);
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<String> announcementJson = new AtomicReference<>();

        var future = QuicClientConnector.connectOrFallback(address, connection, () -> {
            throw new IllegalStateException("TCP fallback is not allowed in this probe");
        });

        if (!future.await(15, TimeUnit.SECONDS) || !future.isSuccess()) {
            System.out.println("PROBE FAILED: QUIC connect did not complete: " + future.cause());
            System.exit(1);
        }
        io.netty.handler.codec.quic.QuicStreamChannel master = future.channel().attr(QuicAttributes.MASTER_STREAM).get();
        if (master == null) {
            System.out.println("PROBE FAILED: no master stream on the datagram channel");
            System.exit(1);
        }

        master.pipeline().addFirst("probe_sniffer", new io.netty.channel.ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof io.netty.buffer.ByteBuf buf) {
                    String text = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                    if (text.contains("quicify")) {
                        announcementJson.set(text);
                    }
                }
                ctx.fireChannelRead(msg);
            }
        });

        connection.initiateServerboundStatusConnection(host, port, new ClientStatusPacketListener() {
            @Override
            public void handleStatusResponse(@NonNull ClientboundStatusResponsePacket packet) {
                result.set("version=" + packet.status().version().map(v -> v.name() + "/" + v.protocol()).orElse("?") + " players=" + packet.status().players().map(p -> p.online() + "/" + p.max()).orElse("?") + " description=" + packet.status().description().getString());
                connection.disconnect(Component.literal("probe done"));
                done.countDown();
            }

            @Override
            public void handlePongResponse(@NonNull ClientboundPongResponsePacket packet) {
            }

            @Override
            public void onDisconnect(@NonNull DisconnectionDetails details) {
                if (result.get() == null) {
                    failure.set("disconnected: " + details.reason().getString());
                }
                done.countDown();
            }

            @Override
            public boolean isAcceptingMessages() {
                return connection.isConnected();
            }
        });
        connection.send(ServerboundStatusRequestPacket.INSTANCE);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (done.getCount() > 0 && System.nanoTime() < deadline) {
            if (connection.isConnected()) {
                connection.tick();
            }
            Thread.sleep(50);
        }

        if (result.get() != null) {
            System.out.println("PROBE OK over QUIC: " + result.get());
            System.out.println("PROBE announcement on wire: " + (announcementJson.get() != null ? "FOUND" : "MISSING"));
            System.exit(announcementJson.get() != null ? 0 : 1);
        } else {
            System.out.println("PROBE FAILED: " + (failure.get() != null ? failure.get() : "timeout"));
            System.exit(1);
        }
    }
}
