package zapmc.quicify.quic;

import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicConnectionPathStats;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QuicPathSampler {

    private static final AttributeKey<QuicPathSampler> KEY = AttributeKey.valueOf("quicify:path_sampler");

    private static final long REFRESH_MS = 1000L;

    private final AtomicBoolean collecting = new AtomicBoolean();

    private volatile boolean sampled;

    private volatile long sampledAt;

    private volatile long rttNanos;

    private volatile long cwnd;

    private volatile long sent;

    private volatile long lost;

    private QuicPathSampler() {
    }

    public static QuicPathSampler of(QuicChannel quicChannel) {
        Attribute<QuicPathSampler> attribute = quicChannel.attr(KEY);
        QuicPathSampler sampler = attribute.get();
        if (sampler != null) {
            return sampler;
        }
        QuicPathSampler created = new QuicPathSampler();
        QuicPathSampler raced = attribute.setIfAbsent(created);
        return raced != null ? raced : created;
    }

    @SuppressWarnings("resource")
    public void refresh(QuicChannel quicChannel) {
        if (System.currentTimeMillis() - sampledAt < REFRESH_MS || !collecting.compareAndSet(false, true)) {
            return;
        }
        try {
            quicChannel.eventLoop().execute(() -> collect(quicChannel));
        } catch (RejectedExecutionException ignored) {
            done();
        }
    }

    public boolean sampled() {
        return sampled;
    }

    public int rttMillis() {
        return (int) Math.min(Math.round(rttNanos / 1_000_000.0), Integer.MAX_VALUE);
    }

    public long cwnd() {
        return cwnd;
    }

    public long sent() {
        return sent;
    }

    public long lost() {
        return lost;
    }

    private void collect(QuicChannel quicChannel) {
        if (!quicChannel.isActive()) {
            done();
            return;
        }
        quicChannel.collectPathStats(0).addListener(future -> {
            if (future.isSuccess() && future.getNow() instanceof QuicConnectionPathStats stats) {
                store(stats);
            }
            done();
        });
    }

    private void store(QuicConnectionPathStats stats) {
        rttNanos = stats.rtt();
        cwnd = stats.cwnd();
        sent = stats.sent();
        lost = stats.lost();
        sampled = true;
    }

    private void done() {
        sampledAt = System.currentTimeMillis();
        collecting.set(false);
    }
}
