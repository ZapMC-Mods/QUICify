package zapmc.quicify.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

public class VarintFrameDecoder extends ByteToMessageDecoder {

    private static final int MAX_LENGTH_BYTES = 3;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();

        int length = 0;
        for (int read = 0; read < MAX_LENGTH_BYTES; read++) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            byte current = in.readByte();
            length |= (current & 0x7F) << read * 7;
            if (!Varints.hasContinuationBit(current)) {
                if (in.readableBytes() < length) {
                    in.resetReaderIndex();
                    return;
                }
                out.add(in.readRetainedSlice(length));
                return;
            }
        }
        throw new CorruptedFrameException("length wider than 21-bit");
    }
}
