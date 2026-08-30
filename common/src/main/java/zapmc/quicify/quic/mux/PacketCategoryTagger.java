package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;

public final class PacketCategoryTagger extends ChannelOutboundHandlerAdapter implements CategorySource {

    public static final String NAME = "quicify_tag";

    private final QuicMuxSession session;

    private @Nullable PacketCategory current;

    public PacketCategoryTagger(QuicMuxSession session) {
        this.session = session;
    }

    @Override
    public @Nullable PacketCategory current() {
        return current;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof Packet<?> packet)) {
            ctx.write(msg, promise);
            return;
        }

        PacketType<?> type = packet.type();
        boolean barrier = PacketRouting.isBarrier(type);
        if (barrier) {
            session.beginBarrier(PacketRouting.isTerminal(type));
        }

        PacketCategory previous = current;
        current = PacketRouting.categoryOf(type);
        try {
            ctx.write(msg, promise);
        } finally {
            current = previous;
        }

        if (barrier) {
            session.finishBarrier();
            if (PacketRouting.isPlayEntry(type)) {
                session.arm();
            }
        }
    }
}
