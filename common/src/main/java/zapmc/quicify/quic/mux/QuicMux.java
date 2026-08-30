package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import net.minecraft.network.BandwidthDebugMonitor;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;

public final class QuicMux {

    public static final String METER_NAME = StreamMeter.NAME;

    public static boolean negotiated(QuicChannel quicChannel) {
        return MuxStreams.negotiated(quicChannel);
    }

    public static void install(QuicChannel quicChannel, QuicStreamChannel master, boolean clientSide, @Nullable BandwidthDebugMonitor bandwidth) {
        MuxStats stats = new MuxStats(bandwidth == null ? null : bandwidth::onReceive);
        quicChannel.attr(MuxStats.KEY).set(stats);
        master.pipeline().addFirst(StreamMeter.NAME, new StreamMeter(stats, PacketCategory.CONTROL));
        master.pipeline().addAfter("splitter", FrameCounter.NAME, new FrameCounter(stats));

        if (!QuicifyConfigs.multiplexing() || !negotiated(quicChannel)) {
            MuxStreams.markDisabled(quicChannel);
            MuxStreams.drainPending(quicChannel, null);
            return;
        }

        QuicMuxSession session = new QuicMuxSession(quicChannel, master, clientSide, stats, "splitter");

        ChannelPipeline pipeline = master.pipeline();
        PacketCategoryTagger tagger = new PacketCategoryTagger(session);
        pipeline.addAfter(outboundHandlerName(pipeline), PacketCategoryTagger.NAME, tagger);
        pipeline.addAfter(StreamMeter.NAME, StreamRouter.NAME, new StreamRouter(session, tagger));
        pipeline.addBefore("packet_handler", BarrierGate.NAME, new BarrierGate(session, PacketRouting::barrierOf));

        MuxStreams.whenActive(master, () -> {
            quicChannel.attr(QuicMuxSession.KEY).set(session);
            if (clientSide) {
                session.openSecondaries();
            } else {
                MuxStreams.drainPending(quicChannel, session);
            }
        });

        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC multiplexing negotiated ({} secondary streams)", PacketCategory.SECONDARY_COUNT);
        }
    }

    public static void acceptSecondary(QuicChannel quicChannel, QuicStreamChannel stream) {
        MuxStreams.acceptSecondary(quicChannel, stream);
    }

    private static String outboundHandlerName(ChannelPipeline pipeline) {
        return pipeline.get("encoder") != null ? "encoder" : "outbound_config";
    }
}
