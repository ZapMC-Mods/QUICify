package zapmc.quicify.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import zapmc.quicify.QuicifyConfigs;

import java.util.concurrent.TimeUnit;

public final class QuicTuning {

    private static final int DATAGRAM_QUEUE_LENGTH = 1024;

    private static <B extends QuicCodecBuilder<B>> B applyToBoth(B builder) {
        if (QuicifyConfigs.datagrams()) {
            builder.datagram(DATAGRAM_QUEUE_LENGTH, DATAGRAM_QUEUE_LENGTH);
        }
        return builder
                .congestionControlAlgorithm(QuicifyConfigs.congestionControl())
                .discoverPmtu(true)
                .maxSendUdpPayloadSize(1350)
                .maxRecvUdpPayloadSize(1500)
                .maxAckDelay(10, TimeUnit.MILLISECONDS)
                .initialMaxStreamsUnidirectional(0)
                .grease(false)
                .localConnectionIdLength(12);
    }

    public static QuicServerCodecBuilder applyTo(QuicServerCodecBuilder builder) {
        return applyToBoth(builder)
                .initialCongestionWindowPackets(32);
    }

    public static QuicClientCodecBuilder applyTo(QuicClientCodecBuilder builder) {
        return applyToBoth(builder);
    }

    public static Bootstrap applyTo(Bootstrap bootstrap, int socketBufferBytes) {
        return bootstrap
                .option(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(2048).maxMessagesPerRead(16))
                .option(ChannelOption.SO_RCVBUF, socketBufferBytes)
                .option(ChannelOption.SO_SNDBUF, socketBufferBytes);
    }
}
