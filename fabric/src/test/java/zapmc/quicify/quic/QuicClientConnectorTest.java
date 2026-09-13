package zapmc.quicify.quic;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zapmc.quicify.QuicifyConfig.ConnectMode;
import zapmc.quicify.QuicifyFzzyConfigs;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicClientConnectorTest {

    private static final InetSocketAddress NOWHERE = new InetSocketAddress("127.0.0.1", 1);

    private EmbeddedChannel fallbackChannel;

    private AtomicBoolean fallbackUsed;

    @BeforeEach
    void resetConfig() {
        fallbackChannel = new EmbeddedChannel();
        fallbackUsed = new AtomicBoolean(false);
        QuicifyFzzyConfigs.INSTANCE.enabled.accept(true);
        QuicifyFzzyConfigs.INSTANCE.connectMode.accept(ConnectMode.AUTO);
        QuicifyFzzyConfigs.INSTANCE.connectTimeoutMs.accept(250);
        QuicBackoff.INSTANCE.recordSuccess(NOWHERE);
    }

    @AfterEach
    void restoreConfig() {
        QuicifyFzzyConfigs.INSTANCE.enabled.accept(true);
        QuicifyFzzyConfigs.INSTANCE.connectMode.accept(ConnectMode.AUTO);
        QuicifyFzzyConfigs.INSTANCE.connectTimeoutMs.accept(3000);
        QuicBackoff.INSTANCE.recordSuccess(NOWHERE);
        fallbackChannel.finishAndReleaseAll();
    }

    private ChannelFuture fallback() {
        fallbackUsed.set(true);
        return fallbackChannel.newSucceededFuture();
    }

    private ChannelFuture connect() {
        return QuicClientConnector.connectOrFallback(NOWHERE, new Connection(PacketFlow.CLIENTBOUND), this::fallback);
    }

    @Test
    void aDisabledModNeverLeavesTheFallbackPath() {
        QuicifyFzzyConfigs.INSTANCE.enabled.accept(false);

        ChannelFuture future = connect();

        assertTrue(fallbackUsed.get());
        assertTrue(future.isSuccess());
    }

    @Test
    void forceTcpNeverLeavesTheFallbackPath() {
        QuicifyFzzyConfigs.INSTANCE.connectMode.accept(ConnectMode.FORCE_TCP);

        ChannelFuture future = connect();

        assertTrue(fallbackUsed.get());
        assertTrue(future.isSuccess());
    }

    @Test
    void aCoolingDownServerSkipsStraightToTcp() {
        QuicBackoff.INSTANCE.recordFailure(NOWHERE);

        ChannelFuture future = connect();

        assertTrue(fallbackUsed.get());
        assertTrue(future.isSuccess());
    }

    @Test
    void forceQuicIgnoresTheCooldownAndStillAttempts() {
        QuicBackoff.INSTANCE.recordFailure(NOWHERE);
        QuicifyFzzyConfigs.INSTANCE.connectMode.accept(ConnectMode.FORCE_QUIC);

        ChannelFuture future = connect();
        future.awaitUninterruptibly();

        assertFalse(fallbackUsed.get());
        assertFalse(future.isSuccess());
    }

    @Test
    void anUnreachableServerFallsBackToTcp() {
        ChannelFuture future = connect();
        future.awaitUninterruptibly();

        assertTrue(fallbackUsed.get());
        assertTrue(future.isSuccess());
    }

    @Test
    void anUnreachableServerRecordsACooldown() {
        connect().awaitUninterruptibly();

        assertTrue(QuicBackoff.INSTANCE.isCoolingDown(NOWHERE));
    }

    @Test
    void forceQuicFailsInsteadOfFallingBack() {
        QuicifyFzzyConfigs.INSTANCE.connectMode.accept(ConnectMode.FORCE_QUIC);

        ChannelFuture future = connect();
        future.awaitUninterruptibly();

        assertFalse(fallbackUsed.get());
        assertFalse(future.isSuccess());
        assertNotNull(future.cause());
    }

    @Test
    void aCancelledAttemptNeverCompletesSuccessfully() {
        ChannelFuture future = connect();
        future.cancel(true);

        assertTrue(future.isCancelled());
        assertThrows(java.util.concurrent.CancellationException.class, future::sync);
    }
}
