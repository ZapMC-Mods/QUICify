package zapmc.quicify.quic.mux;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MuxStatsTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    private final MuxStats stats = new MuxStats(null, now::get);

    @Test
    void aFreshConnectionHasNotBeenSilentYet() {
        assertEquals(0L, stats.silentMillis());
    }

    @Test
    void silenceGrowsWhileNothingArrives() {
        now.addAndGet(20_000);

        assertEquals(20_000L, stats.silentMillis());
    }

    @Test
    void aFrameOnASecondaryClearsTheSilenceJustLikeOneOnTheMaster() {
        now.addAndGet(20_000);
        stats.recordRx(PacketCategory.WORLD);
        assertEquals(0L, stats.silentMillis(), "a peer that only talks on secondary streams would read as silent and be kicked while it is healthy");

        now.addAndGet(5_000);
        stats.recordRx(PacketCategory.CONTROL);

        assertEquals(0L, stats.silentMillis());
    }

    @Test
    void aDatagramCountsAsActivity() {
        now.addAndGet(20_000);
        stats.recordDatagramRx();

        assertEquals(0L, stats.silentMillis());
    }
}
