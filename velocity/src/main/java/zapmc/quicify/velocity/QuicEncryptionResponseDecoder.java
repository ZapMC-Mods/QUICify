package zapmc.quicify.velocity;

import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.EncryptionResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import zapmc.quicify.Quicify;

public final class QuicEncryptionResponseDecoder extends ChannelInboundHandlerAdapter {

    public static final String NAME = "quicify_key";

    private static final int UNKNOWN = -1;

    private final MinecraftConnection connection;

    private int expectedId = UNKNOWN;

    public QuicEncryptionResponseDecoder(MinecraftConnection connection) {
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame) || connection.getState() != StateRegistry.LOGIN) {
            ctx.fireChannelRead(msg);
            return;
        }

        int readerIndex = frame.readerIndex();
        boolean mine;
        try {
            mine = frame.isReadable() && ProtocolUtils.readVarInt(frame) == expectedId();
        } catch (RuntimeException e) {
            mine = false;
        }
        if (!mine) {
            frame.readerIndex(readerIndex);
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            QuicEncryptionResponsePacket packet = new QuicEncryptionResponsePacket();
            packet.decode(frame, ProtocolUtils.Direction.SERVERBOUND, connection.getProtocolVersion());
            ctx.fireChannelRead(packet);
        } catch (RuntimeException e) {
            Quicify.LOGGER.warn("QUIC login key packet could not be read ({}), closing the connection", e.toString());
            connection.close(true);
        } finally {
            frame.release();
        }
    }

    private int expectedId() {
        if (expectedId == UNKNOWN) {
            expectedId = StateRegistry.LOGIN
                    .getProtocolRegistry(ProtocolUtils.Direction.SERVERBOUND, connection.getProtocolVersion())
                    .getPacketId(new EncryptionResponsePacket());
        }
        return expectedId;
    }
}
