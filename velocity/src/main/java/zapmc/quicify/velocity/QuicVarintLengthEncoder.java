package zapmc.quicify.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import zapmc.quicify.quic.Varints;

public final class QuicVarintLengthEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        int length = msg.readableBytes();
        int size = Varints.getByteSize(length) + length;
        return preferDirect ? ctx.alloc().ioBuffer(size, size) : ctx.alloc().heapBuffer(size, size);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        Varints.write(out, msg.readableBytes());
        out.writeBytes(msg);
    }
}
