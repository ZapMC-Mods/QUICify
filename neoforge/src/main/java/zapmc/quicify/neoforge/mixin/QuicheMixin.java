package zapmc.quicify.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.neoforge.QuicNativeLoader;

@Mixin(targets = "io.netty.handler.codec.quic.Quiche", remap = false)
public abstract class QuicheMixin {

    @Inject(method = "loadNativeLibrary", at = @At("HEAD"), cancellable = true, remap = false)
    private static void quicify$loadFromModClassLoader(CallbackInfo ci) {
        QuicNativeLoader.load();
        ci.cancel();
    }
}
