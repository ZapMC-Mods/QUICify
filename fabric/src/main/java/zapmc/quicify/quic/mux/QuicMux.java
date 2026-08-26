package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import net.minecraft.network.BandwidthDebugMonitor;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;

import javax.net.ssl.SSLEngine;
import java.util.ArrayDeque;
import java.util.Deque;

public final class QuicMux {

    public static final String METER_NAME = StreamMeter.NAME;

    private static final AttributeKey<Deque<QuicStreamChannel>> PENDING_SECONDARIES = AttributeKey.valueOf("quicify:pending_secondaries");

    private static final AttributeKey<Boolean> MUX_DISABLED = AttributeKey.valueOf("quicify:mux_disabled");

    public static boolean negotiated(QuicChannel quicChannel) {
        SSLEngine engine = quicChannel.sslEngine();
        return engine != null && QuicProtocol.ALPN.equals(engine.getApplicationProtocol());
    }

    public static void install(QuicChannel quicChannel, QuicStreamChannel master, boolean clientSide, @Nullable BandwidthDebugMonitor bandwidth) {
        MuxStats stats = new MuxStats(bandwidth);
        quicChannel.attr(MuxStats.KEY).set(stats);
        master.pipeline().addFirst(StreamMeter.NAME, new StreamMeter(stats, PacketCategory.CONTROL));
        master.pipeline().addAfter("splitter", FrameCounter.NAME, new FrameCounter(stats));

        if (!QuicifyConfigs.multiplexing() || !negotiated(quicChannel)) {
            quicChannel.attr(MUX_DISABLED).set(Boolean.TRUE);
            drainPending(quicChannel, null);
            return;
        }

        QuicMuxSession session = new QuicMuxSession(quicChannel, master, clientSide, stats);

        ChannelPipeline pipeline = master.pipeline();
        PacketCategoryTagger tagger = new PacketCategoryTagger(session);
        pipeline.addAfter(outboundHandlerName(pipeline), PacketCategoryTagger.NAME, tagger);
        pipeline.addAfter(StreamMeter.NAME, StreamRouter.NAME, new StreamRouter(session, tagger));
        pipeline.addBefore("packet_handler", BarrierGate.NAME, new BarrierGate(session));

        whenActive(master, () -> {
            quicChannel.attr(QuicMuxSession.KEY).set(session);
            if (clientSide) {
                session.openSecondaries();
            } else {
                drainPending(quicChannel, session);
            }
        });

        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC multiplexing negotiated ({} secondary streams)", PacketCategory.SECONDARY_COUNT);
        }
    }

    public static void acceptSecondary(QuicChannel quicChannel, QuicStreamChannel stream) {
        QuicMuxSession session = QuicMuxSession.of(quicChannel);
        if (session != null) {
            SecondaryStreams.acceptOnServer(stream, session);
            return;
        }
        if (Boolean.TRUE.equals(quicChannel.attr(MUX_DISABLED).get())) {
            stream.close();
            return;
        }
        stream.config().setAutoRead(false);
        pending(quicChannel).addLast(stream);
    }

    private static void drainPending(QuicChannel quicChannel, @Nullable QuicMuxSession session) {
        Deque<QuicStreamChannel> parked = quicChannel.attr(PENDING_SECONDARIES).getAndSet(null);
        if (parked == null) {
            return;
        }
        QuicStreamChannel stream;
        while ((stream = parked.pollFirst()) != null) {
            if (session == null) {
                stream.close();
                continue;
            }
            SecondaryStreams.acceptOnServer(stream, session);
            stream.config().setAutoRead(true);
            stream.read();
        }
    }

    @SuppressWarnings("resource")
    private static void whenActive(QuicStreamChannel master, Runnable action) {
        if (master.isActive()) {
            action.run();
        } else {
            master.eventLoop().execute(() -> {
                if (master.isActive()) {
                    action.run();
                }
            });
        }
    }

    private static Deque<QuicStreamChannel> pending(QuicChannel quicChannel) {
        Deque<QuicStreamChannel> parked = quicChannel.attr(PENDING_SECONDARIES).get();
        if (parked == null) {
            parked = new ArrayDeque<>();
            quicChannel.attr(PENDING_SECONDARIES).set(parked);
        }
        return parked;
    }

    private static String outboundHandlerName(ChannelPipeline pipeline) {
        return pipeline.get("encoder") != null ? "encoder" : "outbound_config";
    }
}
