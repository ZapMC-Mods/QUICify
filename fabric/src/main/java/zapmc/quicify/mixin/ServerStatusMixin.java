package zapmc.quicify.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.network.protocol.status.ServerStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicifyServerStatus;
import zapmc.quicify.quic.QuicifyStatusCodec;

@Mixin(ServerStatus.class)
public abstract class ServerStatusMixin implements QuicifyServerStatus {

    @Shadow
    @Mutable
    @Final
    public static Codec<ServerStatus> CODEC;

    @Unique
    @Nullable
    private QuicAnnouncement quicify$announcement;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void quicify$wrapCodec(CallbackInfo ci) {
        CODEC = QuicifyStatusCodec.wrap(CODEC);
    }

    @Override
    public void quicify$setAnnouncement(@Nullable QuicAnnouncement announcement) {
        this.quicify$announcement = announcement;
    }

    @Override
    public @Nullable QuicAnnouncement quicify$announcement() {
        return quicify$announcement;
    }
}
