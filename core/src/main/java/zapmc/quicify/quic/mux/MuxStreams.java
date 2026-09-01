package zapmc.quicify.quic.mux;

import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.QuicProtocol;

import javax.net.ssl.SSLEngine;
import java.util.ArrayDeque;
import java.util.Deque;

public final class MuxStreams {

    private static final AttributeKey<Deque<QuicStreamChannel>> PENDING_SECONDARIES = AttributeKey.valueOf("quicify:pending_secondaries");

    private static final AttributeKey<Boolean> MUX_DISABLED = AttributeKey.valueOf("quicify:mux_disabled");

    private MuxStreams() {
    }

    public static boolean negotiated(QuicChannel quicChannel) {
        SSLEngine engine = quicChannel.sslEngine();
        return engine != null && QuicProtocol.ALPN.equals(engine.getApplicationProtocol());
    }

    public static void acceptSecondary(QuicChannel quicChannel, QuicStreamChannel stream) {
        QuicMuxSession session = QuicMuxSession.of(quicChannel);
        if (session != null) {
            if (session.disabled()) {
                stream.close();
                return;
            }
            if (session.acceptsSecondaries()) {
                SecondaryStreams.acceptOnServer(stream, session);
                return;
            }
        } else if (Boolean.TRUE.equals(quicChannel.attr(MUX_DISABLED).get())) {
            stream.close();
            return;
        }
        stream.config().setAutoRead(false);
        pending(quicChannel).addLast(stream);
    }

    public static void markDisabled(QuicChannel quicChannel) {
        quicChannel.attr(MUX_DISABLED).set(Boolean.TRUE);
    }

    public static void drainPending(QuicChannel quicChannel, @Nullable QuicMuxSession session) {
        Deque<QuicStreamChannel> parked = quicChannel.attr(PENDING_SECONDARIES).getAndSet(null);
        if (parked == null) {
            return;
        }
        QuicStreamChannel stream;
        while ((stream = parked.pollFirst()) != null) {
            if (session == null) {
                stream.close();
                continue;
            }
            SecondaryStreams.acceptOnServer(stream, session);
            stream.config().setAutoRead(true);
            stream.read();
        }
    }

    @SuppressWarnings("resource")
    public static void whenActive(QuicStreamChannel master, Runnable action) {
        if (master.isActive()) {
            action.run();
        } else {
            master.eventLoop().execute(() -> {
                if (master.isActive()) {
                    action.run();
                }
            });
        }
    }

    private static Deque<QuicStreamChannel> pending(QuicChannel quicChannel) {
        Deque<QuicStreamChannel> parked = quicChannel.attr(PENDING_SECONDARIES).get();
        if (parked == null) {
            parked = new ArrayDeque<>();
            quicChannel.attr(PENDING_SECONDARIES).set(parked);
        }
        return parked;
    }
}
