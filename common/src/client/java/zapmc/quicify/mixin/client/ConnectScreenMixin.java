package zapmc.quicify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.QuicifyServerData;
import zapmc.quicify.quic.QuicClientConnector;
import zapmc.quicify.quic.QuicConnectDecision;
import zapmc.quicify.quic.QuicDatagramTransport;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class ConnectScreenMixin {

    @Shadow
    @Final
    ServerData val$server;

    @WrapOperation(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"
            )
    )
    private ChannelFuture quicify$connectViaQuic(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, Operation<ChannelFuture> original) {
        QuicifyServerData serverData = (QuicifyServerData) val$server;
        QuicConnectDecision decision = QuicConnectDecision.resolve(serverData.quicify$hasPingResult(), serverData.quicify$announcement(), address, QuicifyConfigs.connectMode());
        if (!decision.attemptQuic()) {
            return original.call(address, eventLoopGroupHolder, connection);
        }
        return QuicClientConnector.connectOrFallback(decision.target(), connection, QuicDatagramTransport.select(Minecraft.getInstance().options.useNativeTransport()), () -> original.call(address, eventLoopGroupHolder, connection));
    }
}
