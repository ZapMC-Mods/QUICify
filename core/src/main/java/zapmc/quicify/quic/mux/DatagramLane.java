package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicDatagramExtensionEvent;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.quic.QuicAttributes;
import zapmc.quicify.quic.Varints;

import java.nio.BufferUnderflowException;

public final class DatagramLane extends ChannelInboundHandlerAdapter {

    public static final String NAME = "quicify_dgram";

    static final int HEADER = 1;

    private boolean injected;

    public static @Nullable ByteBuf wrap(QuicChannel quicChannel, ByteBuf framed, int generation) {
        Integer capacity = quicChannel.attr(QuicAttributes.DATAGRAM_CAPACITY).get();
        if (capacity == null) {
            return null;
        }
        int body = bodyOffset(framed);
        if (body < 0) {
            return null;
        }
        int length = framed.writerIndex() - body;
        if (length <= 0 || length + HEADER > capacity) {
            return null;
        }
        ByteBuf datagram = quicChannel.alloc().directBuffer(HEADER + length);
        try {
            datagram.writeByte(generation & 0xFF);
            datagram.writeBytes(framed, body, length);
        } catch (Throwable t) {
            datagram.release();
            throw t;
        }
        return datagram;
    }

    static boolean deliver(QuicChannel quicChannel, ByteBuf datagram) {
        MuxStats stats = MuxStats.of(quicChannel);
        QuicMuxSession session = QuicMuxSession.of(quicChannel);
        if (stats == null || session == null || !session.active() || !datagram.isReadable()) {
            return drop(stats, datagram);
        }

        int wireBytes = datagram.readableBytes();
        if (datagram.readUnsignedByte() != (session.generation() & 0xFF)) {
            return drop(stats, datagram);
        }

        ChannelHandlerContext injection = session.master().pipeline().context(session.injectionPoint());
        if (injection == null) {
            return drop(stats, datagram);
        }

        stats.recordWireBytes(wireBytes);
        stats.recordDatagramRx();
        stats.beginInjection();
        try {
            injection.fireChannelRead(datagram);
        } finally {
            stats.endInjection();
        }
        return true;
    }

    private static boolean drop(@Nullable MuxStats stats, @Nullable ByteBuf datagram) {
        ReferenceCountUtil.release(datagram);
        if (stats != null && stats.recordDatagramDropped() == 1L && QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC datagram dropped (stale generation or lane not active), the reliable streams carry on");
        }
        return false;
    }

    private static int bodyOffset(ByteBuf framed) {
        int cursor = framed.readerIndex();
        int end = framed.writerIndex();
        int declared = 0;
        for (int read = 0; read < Varints.MAX_VARINT_SIZE; read++) {
            if (cursor >= end) {
                return -1;
            }
            byte current = framed.getByte(cursor++);
            declared |= (current & 127) << read * 7;
            if (!Varints.hasContinuationBit(current)) {
                return declared == end - cursor ? cursor : -1;
            }
        }
        return -1;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof QuicDatagramExtensionEvent extension && QuicifyConfigs.datagrams()) {
            ctx.channel().attr(QuicAttributes.DATAGRAM_CAPACITY).set(extension.maxLength());
            if (QuicifyConfigs.verbose()) {
                Quicify.LOGGER.info("QUIC datagrams available (up to {} bytes per datagram)", extension.maxLength());
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf datagram) || !(ctx.channel() instanceof QuicChannel quicChannel)) {
            ctx.fireChannelRead(msg);
            return;
        }
        injected |= deliver(quicChannel, datagram);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (injected) {
            injected = false;
            QuicMuxSession session = ctx.channel() instanceof QuicChannel quicChannel ? QuicMuxSession.of(quicChannel) : null;
            ChannelHandlerContext injection = session == null ? null : session.master().pipeline().context(session.injectionPoint());
            if (injection != null) {
                injection.fireChannelReadComplete();
            }
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof BufferUnderflowException || cause instanceof UnsupportedOperationException) {
            drop(ctx.channel() instanceof QuicChannel quicChannel ? MuxStats.of(quicChannel) : null, null);
            return;
        }
        ctx.fireExceptionCaught(cause);
    }
}
