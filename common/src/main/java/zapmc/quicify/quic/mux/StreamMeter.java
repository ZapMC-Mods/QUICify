package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.quic.QuicStreamChannel;

import static zapmc.quicify.quic.mux.MuxStats.NO_STREAM;

final class StreamMeter extends ChannelDuplexHandler {

    static final String NAME = "quicify_meter";

    private final MuxStats stats;

    private final PacketCategory category;

    private long streamId = NO_STREAM;

    StreamMeter(MuxStats stats, PacketCategory category) {
        this.stats = stats;
        this.category = category;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel() instanceof QuicStreamChannel stream) {
            streamId = stream.streamId();
            stats.bindStream(category, streamId);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ByteBuf) {
            stats.recordTx(category);
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            stats.recordWireBytes(buf.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        stats.unbindStream(category, streamId);
        ctx.fireChannelInactive();
    }
}
