package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.quic.VarintFrameDecoder;

import java.util.List;

public final class SecondaryStreams {

    public static ChannelInboundHandlerAdapter clientInitializer(QuicMuxSession session, PacketCategory category) {
        return new ClientInitializer(session, category);
    }

    public static void acceptOnServer(QuicStreamChannel stream, QuicMuxSession session) {
        stream.pipeline().addLast("quicify_hello", new ServerHelloDecoder(session));
    }

    private static void installFrameForwarding(ChannelPipeline pipeline, QuicMuxSession session, PacketCategory category) {
        pipeline.addFirst(StreamMeter.NAME, new StreamMeter(session.stats(), category));
        pipeline.addLast("splitter", new VarintFrameDecoder());
        pipeline.addLast("quicify_merger", new StreamMerger(session, category));
    }

    private static final class ClientInitializer extends ChannelInboundHandlerAdapter {

        private final QuicMuxSession session;

        private final PacketCategory category;

        private ClientInitializer(QuicMuxSession session, PacketCategory category) {
            this.session = session;
            this.category = category;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
            if (!session.registerSecondary(category, stream)) {
                ctx.pipeline().remove(this);
                return;
            }
            ctx.pipeline().addLast("quicify_hello", new ClientHelloDecoder(session, category));
            installFrameForwarding(ctx.pipeline(), session, category);

            ByteBuf hello = ctx.alloc().buffer(1);
            hello.writeByte(category.wireId());
            stream.writeAndFlush(hello);
            ctx.pipeline().remove(this);
        }
    }

    private static final class ClientHelloDecoder extends ByteToMessageDecoder {

        private final QuicMuxSession session;

        private final PacketCategory category;

        private ClientHelloDecoder(QuicMuxSession session, PacketCategory category) {
            this.session = session;
            this.category = category;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!session.disabled()) {
                Quicify.LOGGER.warn("QUIC secondary stream {} closed before its hello was echoed, staying single-stream", category);
                session.disable();
            }
            super.channelInactive(ctx);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (!in.isReadable()) {
                return;
            }
            PacketCategory echoed = PacketCategory.byWireId(in.readByte());
            ctx.pipeline().remove(this);
            if (echoed != category) {
                Quicify.LOGGER.warn("QUIC secondary stream echoed {} instead of {}, staying single-stream", echoed, category);
                session.disable();
                return;
            }
            session.markReady(category);
        }
    }

    private static final class ServerHelloDecoder extends ByteToMessageDecoder {

        private final QuicMuxSession session;

        private ServerHelloDecoder(QuicMuxSession session) {
            this.session = session;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (!in.isReadable()) {
                return;
            }
            PacketCategory category = PacketCategory.byWireId(in.readByte());
            if (category == null) {
                Quicify.LOGGER.warn("QUIC secondary stream announced an unknown category, closing it");
                session.disable();
                ctx.close();
                return;
            }
            QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
            ctx.pipeline().remove(this);
            if (!session.registerSecondary(category, stream)) {
                return;
            }
            installFrameForwarding(ctx.pipeline(), session, category);

            ByteBuf echo = ctx.alloc().buffer(1);
            echo.writeByte(category.wireId());
            stream.writeAndFlush(echo).addListener(_ -> session.markReady(category));
        }
    }

    private static final class StreamMerger extends ChannelInboundHandlerAdapter {

        private final QuicMuxSession session;

        private final PacketCategory category;

        private boolean inputClosed;

        private StreamMerger(QuicMuxSession session, PacketCategory category) {
            this.session = session;
            this.category = category;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ChannelHandlerContext splitter = injectionPoint();
            if (splitter == null) {
                ReferenceCountUtil.release(msg);
                return;
            }
            MuxStats stats = session.stats();
            stats.recordRx(category);
            stats.beginInjection();
            try {
                splitter.fireChannelRead(msg);
            } finally {
                stats.endInjection();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ChannelHandlerContext splitter = injectionPoint();
            if (splitter != null) {
                splitter.fireChannelReadComplete();
            }
        }

        private @Nullable ChannelHandlerContext injectionPoint() {
            return session.master().pipeline().context(session.injectionPoint());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof io.netty.channel.socket.ChannelInputShutdownEvent) {
                closeInput();
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeInput();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            session.fail("secondary stream " + category + " failed: " + cause);
        }

        private void closeInput() {
            if (inputClosed) {
                return;
            }
            inputClosed = true;
            session.onSecondaryInputClosed();
        }
    }
}
