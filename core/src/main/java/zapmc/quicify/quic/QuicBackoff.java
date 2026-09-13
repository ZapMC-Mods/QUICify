package zapmc.quicify.quic;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class QuicBackoff {

    public static final long COOLDOWN_MILLIS = 5 * 60 * 1000;

    public static final QuicBackoff INSTANCE = new QuicBackoff(System::currentTimeMillis);

    private final Map<InetSocketAddress, Long> failures = new ConcurrentHashMap<>();

    private final LongSupplier clock;

    public QuicBackoff(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isCoolingDown(InetSocketAddress address) {
        purgeExpired();
        Long failedAt = failures.get(address);
        return failedAt != null && clock.getAsLong() - failedAt < COOLDOWN_MILLIS;
    }

    public void recordFailure(InetSocketAddress address) {
        purgeExpired();
        failures.put(address, clock.getAsLong());
    }

    public void recordSuccess(InetSocketAddress address) {
        failures.remove(address);
    }

    private void purgeExpired() {
        long now = clock.getAsLong();
        failures.entrySet().removeIf(inetSocketAddressLongEntry -> now - inetSocketAddressLongEntry.getValue() >= COOLDOWN_MILLIS);
    }
}
