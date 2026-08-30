package zapmc.quicify.velocity;

import com.velocitypowered.proxy.network.Connections;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.quic.mux.BarrierGate;
import zapmc.quicify.quic.mux.FrameCounter;
import zapmc.quicify.quic.mux.MuxStats;
import zapmc.quicify.quic.mux.MuxStreams;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.PacketRoutingTable;
import zapmc.quicify.quic.mux.QuicMuxSession;
import zapmc.quicify.quic.mux.StreamMeter;

public final class VelocityMux {

    private VelocityMux() {
    }

    public static void install(QuicChannel quicChannel, QuicStreamChannel master, QuicMinecraftConnection connection) {
        MuxStats stats = new MuxStats(null);
        quicChannel.attr(MuxStats.KEY).set(stats);

        ChannelPipeline pipeline = master.pipeline();
        pipeline.addFirst(StreamMeter.NAME, new StreamMeter(stats, PacketCategory.CONTROL));
        pipeline.addAfter(Connections.FRAME_DECODER, FrameCounter.NAME, new FrameCounter(stats));

        PacketRoutingTable table = routingTable(connection);
        if (!QuicifyConfigs.multiplexing() || !MuxStreams.negotiated(quicChannel) || table == null) {
            MuxStreams.markDisabled(quicChannel);
            MuxStreams.drainPending(quicChannel, null);
            return;
        }

        QuicMuxSession session = new QuicMuxSession(quicChannel, master, false, stats, Connections.FRAME_DECODER);

        pipeline.addAfter(StreamMeter.NAME, FrameRouter.NAME,
                new FrameRouter(session, FrameRouting.outbound(table, connection)));
        pipeline.addAfter(FrameCounter.NAME, BarrierGate.NAME,
                new BarrierGate(session, FrameRouting.inbound(table, connection)::barrier));

        MuxStreams.whenActive(master, () -> {
            quicChannel.attr(QuicMuxSession.KEY).set(session);
            MuxStreams.drainPending(quicChannel, session);
        });

        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC multiplexing negotiated ({} secondary streams)", PacketCategory.SECONDARY_COUNT);
        }
    }

    private static @Nullable PacketRoutingTable routingTable(QuicMinecraftConnection connection) {
        int protocolVersion = connection.getProtocolVersion().getProtocol();
        PacketRoutingTable table = PacketRoutingTable.forProtocol(protocolVersion);
        if (table == null && QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("No QUIC routing table for protocol {}, staying single-stream", protocolVersion);
        }
        return table;
    }
}
