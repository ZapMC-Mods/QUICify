package zapmc.quicify.quic.mux;

import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;

final class StubStream {

    final EmbeddedChannel channel = new EmbeddedChannel();

    final QuicStreamChannel handle;

    int shutdownOutputs;

    boolean blockShutdown;

    ChannelPromise pendingShutdown;

    StubStream(long streamId) {
        handle = MuxStubs.proxy(QuicStreamChannel.class, channel, (method, _) -> switch (method.getName()) {
            case "streamId" -> streamId;
            case "shutdownOutput", "shutdownInput", "shutdown" -> {
                shutdownOutputs++;
                if (!blockShutdown) {
                    yield channel.newSucceededFuture();
                }
                pendingShutdown = channel.newPromise();
                yield pendingShutdown;
            }
            default -> null;
        });
    }
}
