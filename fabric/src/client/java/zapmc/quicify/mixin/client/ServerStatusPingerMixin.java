package zapmc.quicify.mixin.client;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.QuicifyServerData;
import zapmc.quicify.QuicifyServerStatus;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public abstract class ServerStatusPingerMixin {

    @Shadow
    @Final
    ServerData val$data;

    @Inject(method = "handleStatusResponse", at = @At("HEAD"))
    private void quicify$sniffAnnouncement(ClientboundStatusResponsePacket packet, CallbackInfo ci) {
        ((QuicifyServerData) val$data).quicify$setPingResult(((QuicifyServerStatus) (Object) packet.status()).quicify$announcement());
    }
}
