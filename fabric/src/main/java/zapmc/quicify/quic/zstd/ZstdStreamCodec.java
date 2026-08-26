package zapmc.quicify.quic.zstd;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.VarInt;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;

public final class ZstdStreamCodec extends ByteToMessageCodec<ByteBuf> {

    public static final String NAME = "quicify_zstd";

    public static final int MAX_UNCOMPRESSED_LENGTH = 8388608;

    private static final int MAX_FRAME_LENGTH = MAX_UNCOMPRESSED_LENGTH + (MAX_UNCOMPRESSED_LENGTH >> 8) + 1024;

    private static final int RAW = 1;
    private static final int INCOMPLETE = -1;
    private final ZstdParams params;
    private @Nullable ZstdCompressCtx compressor;
    private @Nullable ZstdDecompressCtx decompressor;

    public ZstdStreamCodec(ZstdParams params) {
        this.params = params;
    }

    private static int readVarInt(ByteBuf in) {
        int value = 0;
        for (int i = 0; i < VarInt.MAX_VARINT_SIZE; i++) {
            if (!in.isReadable()) {
                return INCOMPLETE;
            }
            byte read = in.readByte();
            value |= (read & 127) << (i * 7);
            if (!VarInt.hasContinuationBit(read)) {
                return value;
            }
        }
        throw new DecoderException("VarInt too big");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        try {
            ZstdCompressCtx cctx = new ZstdCompressCtx();
            compressor = cctx;
            cctx.setLevel(params.level());
            cctx.setWindowLog(params.windowLog());
            cctx.setChecksum(false);
            decompressor = new ZstdDecompressCtx();
            super.handlerAdded(ctx);
        } catch (Throwable t) {
            release();
            throw t;
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            release();
        }
    }

    private void release() {
        ZstdCompressCtx cctx = compressor;
        compressor = null;
        if (cctx != null) {
            cctx.close();
        }
        ZstdDecompressCtx dctx = decompressor;
        decompressor = null;
        if (dctx != null) {
            dctx.close();
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int length = msg.readableBytes();
        if (length > MAX_UNCOMPRESSED_LENGTH) {
            throw new EncoderException("Packet too big (is " + length + ", should be less than " + MAX_UNCOMPRESSED_LENGTH + ")");
        }
        ZstdCompressCtx cctx = compressor;
        if (length < params.threshold() || cctx == null) {
            VarInt.write(out, length << 1 | RAW);
            out.writeBytes(msg, msg.readerIndex(), length);
            return;
        }

        ByteBuf sourceCopy = null;
        ByteBuf compressed = ctx.alloc().directBuffer((int) Zstd.compressBound(length) + 64);
        try {
            ByteBuffer source;
            if (msg.isDirect() && msg.nioBufferCount() == 1) {
                source = msg.internalNioBuffer(msg.readerIndex(), length);
            } else {
                sourceCopy = ctx.alloc().directBuffer(length);
                sourceCopy.writeBytes(msg, msg.readerIndex(), length);
                source = sourceCopy.internalNioBuffer(0, length);
            }

            while (true) {
                ByteBuffer target = compressed.internalNioBuffer(compressed.writerIndex(), compressed.writableBytes());
                int before = target.position();
                boolean done = cctx.compressDirectByteBufferStream(target, source, EndDirective.FLUSH);
                compressed.writerIndex(compressed.writerIndex() + (target.position() - before));
                if (done) {
                    break;
                }
                compressed.ensureWritable(compressed.capacity());
            }

            int payload = VarInt.getByteSize(length) + compressed.readableBytes();
            VarInt.write(out, payload << 1);
            VarInt.write(out, length);
            out.writeBytes(compressed);
        } finally {
            compressed.release();
            if (sourceCopy != null) {
                sourceCopy.release();
            }
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        int header = readVarInt(in);
        if (header == INCOMPLETE) {
            in.resetReaderIndex();
            return;
        }
        int frameLength = header >>> 1;
        if (frameLength <= 0 || frameLength > MAX_FRAME_LENGTH) {
            throw new DecoderException("Bad zstd frame length " + frameLength);
        }
        if (in.readableBytes() < frameLength) {
            in.resetReaderIndex();
            return;
        }

        if ((header & RAW) != 0) {
            out.add(in.readBytes(frameLength));
            return;
        }

        int frameEnd = in.readerIndex() + frameLength;
        int uncompressedLength = VarInt.read(in);
        if (in.readerIndex() > frameEnd) {
            throw new DecoderException("Truncated zstd frame header");
        }
        int payloadLength = frameEnd - in.readerIndex();

        if (uncompressedLength <= 0 || uncompressedLength > MAX_UNCOMPRESSED_LENGTH) {
            throw new DecoderException("Badly compressed packet - size of " + uncompressedLength + " is larger than protocol maximum of " + MAX_UNCOMPRESSED_LENGTH);
        }
        if (params.validate() && uncompressedLength < params.threshold()) {
            throw new DecoderException("Badly compressed packet - size of " + uncompressedLength + " is below server threshold of " + params.threshold());
        }
        ZstdDecompressCtx dctx = decompressor;
        if (dctx == null) {
            throw new DecoderException("zstd decompression context is already closed");
        }

        ByteBuf sourceCopy = null;
        ByteBuf decompressed = ctx.alloc().directBuffer(uncompressedLength + 1);
        try {
            ByteBuffer source;
            if (in.isDirect() && in.nioBufferCount() == 1) {
                source = in.internalNioBuffer(in.readerIndex(), payloadLength);
            } else {
                sourceCopy = ctx.alloc().directBuffer(payloadLength);
                sourceCopy.writeBytes(in, in.readerIndex(), payloadLength);
                source = sourceCopy.internalNioBuffer(0, payloadLength);
            }

            ByteBuffer target = decompressed.internalNioBuffer(0, uncompressedLength + 1);
            int start = target.position();
            while (source.hasRemaining()) {
                int produced = target.position();
                dctx.decompressDirectByteBufferStream(target, source);
                if (target.position() == produced && source.hasRemaining()) {
                    throw new DecoderException("Badly compressed packet - decompressed payload exceeds declared size " + uncompressedLength);
                }
            }
            int actual = target.position() - start;
            if (actual != uncompressedLength) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + actual + " does not match declared size " + uncompressedLength);
            }
            decompressed.writerIndex(uncompressedLength);
            in.readerIndex(frameEnd);
            out.add(decompressed);
            decompressed = null;
        } finally {
            if (decompressed != null) {
                decompressed.release();
            }
            if (sourceCopy != null) {
                sourceCopy.release();
            }
        }
    }
}
