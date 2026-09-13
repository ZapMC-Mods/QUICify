package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public final class FrameCounter extends ChannelInboundHandlerAdapter {

    public static final String NAME = "quicify_count";

    private final MuxStats stats;

    public FrameCounter(MuxStats stats) {
        this.stats = stats;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!stats.injecting()) {
            stats.recordRx(PacketCategory.CONTROL);
        }
        ctx.fireChannelRead(msg);
    }
}
