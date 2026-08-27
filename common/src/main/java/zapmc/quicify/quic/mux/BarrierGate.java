package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BarrierGate extends ChannelInboundHandlerAdapter {

    public static final String NAME = "quicify_barrier";

    private static final int MAX_HELD = 4096;

    private final QuicMuxSession session;

    private final Deque<Object> held = new ArrayDeque<>();

    private @Nullable ChannelHandlerContext context;

    private @Nullable Object heldBarrier;

    private boolean holding;

    private boolean armAfterRelease;

    public BarrierGate(QuicMuxSession session) {
        this.session = session;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.context = ctx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (holding) {
            if (held.size() >= MAX_HELD) {
                Quicify.LOGGER.warn("QUIC barrier held more than {} packets, staying single-stream", MAX_HELD);
                session.disable();
                release();
                ctx.fireChannelRead(msg);
                return;
            }
            held.addLast(msg);
            return;
        }
        deliver(ctx, msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        discard();
        ctx.fireChannelInactive();
    }

    private void deliver(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet<?> packet) {
            PacketType<?> type = packet.type();
            if (PacketRouting.isBarrier(type)) {
                session.beginBarrier(PacketRouting.isTerminal(type));
                session.finishBarrier();
                if (session.draining()) {
                    holding = true;
                    armAfterRelease = PacketRouting.isPlayEntry(type);
                    heldBarrier = msg;
                    session.onDrainComplete(this::release);
                    return;
                }
                ctx.fireChannelRead(msg);
                if (PacketRouting.isPlayEntry(type)) {
                    session.arm();
                }
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void release() {
        ChannelHandlerContext ctx = context;
        if (ctx == null) {
            discard();
            return;
        }
        holding = false;
        Object barrier = heldBarrier;
        heldBarrier = null;
        if (barrier != null) {
            ctx.fireChannelRead(barrier);
        }
        if (armAfterRelease) {
            armAfterRelease = false;
            session.arm();
        }
        while (!holding) {
            Object msg = held.pollFirst();
            if (msg == null) {
                break;
            }
            deliver(ctx, msg);
        }
        ctx.fireChannelReadComplete();
    }

    private void discard() {
        holding = false;
        armAfterRelease = false;
        Object barrier = heldBarrier;
        heldBarrier = null;
        if (barrier != null) {
            ReferenceCountUtil.release(barrier);
        }
        Object msg;
        while ((msg = held.pollFirst()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }
}
