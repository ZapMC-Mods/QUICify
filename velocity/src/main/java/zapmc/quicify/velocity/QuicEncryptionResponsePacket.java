package zapmc.quicify.velocity;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public final class QuicEncryptionResponsePacket implements MinecraftPacket {

    private byte[] sharedSecret = new byte[0];

    private byte[] challenge = new byte[0];

    public byte[] sharedSecret() {
        return sharedSecret;
    }

    public byte[] challenge() {
        return challenge;
    }

    @Override
    public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
        sharedSecret = ProtocolUtils.readByteArray(buf);
        challenge = ProtocolUtils.readByteArray(buf);
    }

    @Override
    public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
        throw new UnsupportedOperationException("this packet is only ever read");
    }

    @Override
    public boolean handle(MinecraftSessionHandler handler) {
        if (!(handler instanceof QuicLoginSessionHandler quic)) {
            return false;
        }
        quic.handle(this);
        return true;
    }
}
