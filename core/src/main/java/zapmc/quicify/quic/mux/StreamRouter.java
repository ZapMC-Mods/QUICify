package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public final class StreamRouter extends ChannelOutboundHandlerAdapter {

    public static final String NAME = "quicify_route";

    private final QuicMuxSession session;

    private final CategorySource tagger;

    public StreamRouter(QuicMuxSession session, CategorySource tagger) {
        this.session = session;
        this.tagger = tagger;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        session.bindRouter(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ByteBuf buf) {
            PacketCategory category = tagger.current();
            if (category != null && category.secondary() && session.route(category, buf, promise)) {
                return;
            }
        }
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        session.flushSecondaries();
        ctx.flush();
    }
}
