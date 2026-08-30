package zapmc.quicify.quic.zstd;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.quic.mux.QuicMuxSession;
import zapmc.quicify.quic.mux.StreamMeter;

public final class QuicCompression {

    private static final AttributeKey<ZstdParams> KEY = AttributeKey.valueOf("quicify:zstd_params");

    public static @Nullable ZstdParams params(@Nullable QuicChannel quicChannel) {
        return quicChannel == null ? null : quicChannel.attr(KEY).get();
    }

    public static void setup(@Nullable QuicChannel quicChannel, Channel master, int threshold, boolean validate) {
        if (quicChannel == null) {
            return;
        }
        if (threshold < 0) {
            quicChannel.attr(KEY).set(null);
            remove(master.pipeline());
            return;
        }
        ZstdParams params = new ZstdParams(threshold, validate, QuicifyConfigs.compressionLevel(), QuicifyConfigs.compressionWindowLog());
        quicChannel.attr(KEY).set(params);
        install(master.pipeline(), params);
        QuicMuxSession session = QuicMuxSession.of(quicChannel);
        if (session != null) {
            session.installCompression(params);
        }
        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC zstd compression enabled (threshold {}, level {}, windowLog {})", threshold, params.level(), params.windowLog());
        }
    }

    public static void install(ChannelPipeline pipeline, ZstdParams params) {
        if (pipeline.get(ZstdStreamCodec.NAME) != null) {
            return;
        }
        ZstdStreamCodec codec = new ZstdStreamCodec(params);
        if (pipeline.get(StreamMeter.NAME) != null) {
            pipeline.addAfter(StreamMeter.NAME, ZstdStreamCodec.NAME, codec);
        } else {
            pipeline.addFirst(ZstdStreamCodec.NAME, codec);
        }
    }

    public static void installIfEnabled(@Nullable QuicChannel quicChannel, ChannelPipeline pipeline) {
        ZstdParams params = params(quicChannel);
        if (params != null) {
            install(pipeline, params);
        }
    }

    private static void remove(ChannelPipeline pipeline) {
        if (pipeline.get(ZstdStreamCodec.NAME) != null) {
            pipeline.remove(ZstdStreamCodec.NAME);
        }
    }
}
