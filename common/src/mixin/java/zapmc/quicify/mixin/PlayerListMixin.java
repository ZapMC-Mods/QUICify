package zapmc.quicify.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zapmc.quicify.quic.QuicifyConnection;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Redirect(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V"
            )
    )
    private void quicify$tagQuicLogin(Logger logger, String format, Object[] args, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            logger.info(format + " using QUIC", args);
        } else {
            logger.info(format, args);
        }
    }
}
