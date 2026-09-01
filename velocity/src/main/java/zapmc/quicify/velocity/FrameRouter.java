package zapmc.quicify.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import zapmc.quicify.quic.mux.BarrierClassifier;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.QuicMuxSession;

public final class FrameRouter extends ChannelOutboundHandlerAdapter {

    public static final String NAME = "quicify_route";

    private final QuicMuxSession session;

    private final FrameRouting routing;

    private boolean insideBundle;

    public FrameRouter(QuicMuxSession session, FrameRouting routing) {
        this.session = session;
        this.routing = routing;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        session.bindRouter(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.write(msg, promise);
            return;
        }

        BarrierClassifier.Barrier barrier = routing.barrier(frame);
        if (barrier != null) {
            insideBundle = false;
            session.beginBarrier(barrier.terminal());
            ctx.write(msg, promise);
            session.finishBarrier();
            if (barrier.playEntry()) {
                session.arm();
            }
            return;
        }

        if (routing.isBundleDelimiter(frame)) {
            insideBundle = !insideBundle;
            ctx.write(msg, promise);
            return;
        }

        if (!insideBundle && routing.isDatagram(frame) && session.routeDatagram(frame, promise)) {
            return;
        }

        PacketCategory category = insideBundle ? PacketCategory.CONTROL : routing.category(frame);
        if (category.secondary() && session.route(category, frame, promise)) {
            return;
        }
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        session.flushSecondaries();
        session.flushDatagrams();
        ctx.flush();
    }
}
