package zapmc.quicify.mixin.client;

import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundHelloPacket.class)
public interface ClientboundHelloPacketAccessor {

    @Accessor("publicKey")
    byte[] quicify$rawPublicKey();
}
