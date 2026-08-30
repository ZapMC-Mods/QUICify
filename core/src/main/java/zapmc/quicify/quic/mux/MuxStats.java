package zapmc.quicify.quic.mux;

import io.netty.handler.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLongArray;

public final class MuxStats {

    public static final AttributeKey<MuxStats> KEY = AttributeKey.valueOf("quicify:mux_stats");

    public static final long NO_STREAM = -1L;

    private static final int COUNT = PacketCategory.values().length;

    private final AtomicLongArray txPackets = new AtomicLongArray(COUNT);

    private final AtomicLongArray rxPackets = new AtomicLongArray(COUNT);

    private final AtomicLongArray streamIds = new AtomicLongArray(COUNT);

    private final @Nullable RxMeter bandwidth;

    private boolean injecting;

    public MuxStats(@Nullable RxMeter bandwidth) {
        this.bandwidth = bandwidth;
        for (int i = 0; i < COUNT; i++) {
            streamIds.set(i, NO_STREAM);
        }
    }

    public static @Nullable MuxStats of(@Nullable QuicChannel channel) {
        return channel == null ? null : channel.attr(KEY).get();
    }

    void recordTx(PacketCategory category) {
        txPackets.incrementAndGet(category.ordinal());
    }

    void recordRx(PacketCategory category) {
        rxPackets.incrementAndGet(category.ordinal());
    }

    void recordWireBytes(int bytes) {
        if (bandwidth != null) {
            bandwidth.onReceive(bytes);
        }
    }

    void beginInjection() {
        injecting = true;
    }

    void endInjection() {
        injecting = false;
    }

    boolean injecting() {
        return injecting;
    }

    void bindStream(PacketCategory category, long streamId) {
        streamIds.set(category.ordinal(), streamId);
    }

    void unbindStream(PacketCategory category, long streamId) {
        streamIds.compareAndSet(category.ordinal(), streamId, NO_STREAM);
    }

    public long txPackets(PacketCategory category) {
        return txPackets.get(category.ordinal());
    }

    public long rxPackets(PacketCategory category) {
        return rxPackets.get(category.ordinal());
    }

    public long streamId(PacketCategory category) {
        return streamIds.get(category.ordinal());
    }
}
