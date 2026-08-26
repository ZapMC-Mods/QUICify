package zapmc.quicify.quic;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicBackoffTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    private final QuicBackoff backoff = new QuicBackoff(now::get);

    private static InetSocketAddress server(String host) {
        return InetSocketAddress.createUnresolved(host, 25565);
    }

    @Test
    void anUntriedServerIsNotCoolingDown() {
        assertFalse(backoff.isCoolingDown(server("mc.example.com")));
    }

    @Test
    void aFailureSuppressesTheNextAttempt() {
        backoff.recordFailure(server("mc.example.com"));

        assertTrue(backoff.isCoolingDown(server("mc.example.com")));
    }

    @Test
    void theCooldownIsPerServer() {
        backoff.recordFailure(server("mc.example.com"));

        assertFalse(backoff.isCoolingDown(server("other.example.com")));
    }

    @Test
    void theCooldownExpires() {
        backoff.recordFailure(server("mc.example.com"));
        now.addAndGet(QuicBackoff.COOLDOWN_MILLIS - 1);
        assertTrue(backoff.isCoolingDown(server("mc.example.com")));

        now.addAndGet(1);
        assertFalse(backoff.isCoolingDown(server("mc.example.com")));
    }

    @Test
    void aSuccessClearsTheCooldownImmediately() {
        backoff.recordFailure(server("mc.example.com"));
        backoff.recordSuccess(server("mc.example.com"));

        assertFalse(backoff.isCoolingDown(server("mc.example.com")));
    }

    @Test
    void aLaterFailureRestartsTheCooldown() {
        backoff.recordFailure(server("mc.example.com"));
        now.addAndGet(QuicBackoff.COOLDOWN_MILLIS - 1);
        backoff.recordFailure(server("mc.example.com"));

        now.addAndGet(QuicBackoff.COOLDOWN_MILLIS - 1);
        assertTrue(backoff.isCoolingDown(server("mc.example.com")));
    }

    @Test
    void expiredEntriesAreNotKept() {
        backoff.recordFailure(server("mc.example.com"));
        now.addAndGet(QuicBackoff.COOLDOWN_MILLIS);
        backoff.recordFailure(server("other.example.com"));

        assertFalse(backoff.isCoolingDown(server("mc.example.com")));
        assertTrue(backoff.isCoolingDown(server("other.example.com")));
    }
}
