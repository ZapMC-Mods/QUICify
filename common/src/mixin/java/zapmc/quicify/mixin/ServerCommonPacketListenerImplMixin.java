package zapmc.quicify.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import io.netty.handler.codec.quic.QuicChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zapmc.quicify.quic.QuicPathSampler;
import zapmc.quicify.quic.QuicifyConnection;
import zapmc.quicify.quic.mux.MuxStats;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @Unique
    private static final long QUICIFY_LIVENESS_TIMEOUT_MILLIS = 30_000L;
    @Shadow
    @Final
    protected Connection connection;

    @Inject(method = "keepConnectionAlive", at = @At("HEAD"))
    private void quicify$sampleRoundTripTime(CallbackInfo ci) {
        QuicChannel channel = quicify$quicChannel();
        if (channel != null) {
            QuicPathSampler.of(channel).refresh(channel);
        }
    }

    @WrapWithCondition(method = "keepConnectionAlive", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"))
    private boolean quicify$leaveLivenessToTheQuicConnection(ServerCommonPacketListenerImpl listener, Component reason) {
        QuicChannel channel = quicify$quicChannel();
        if (channel == null) {
            return true;
        }
        MuxStats stats = MuxStats.of(channel);
        return stats == null || stats.silentMillis() >= QUICIFY_LIVENESS_TIMEOUT_MILLIS;
    }

    @Inject(method = "latency", at = @At("HEAD"), cancellable = true)
    private void quicify$reportQuicRoundTripTime(CallbackInfoReturnable<Integer> cir) {
        QuicChannel channel = quicify$quicChannel();
        if (channel == null) {
            return;
        }
        QuicPathSampler sampler = QuicPathSampler.of(channel);
        if (sampler.sampled()) {
            cir.setReturnValue(sampler.rttMillis());
        }
    }

    @Unique
    private @Nullable QuicChannel quicify$quicChannel() {
        return connection instanceof QuicifyConnection duck ? duck.quicify$quicChannel() : null;
    }
}
