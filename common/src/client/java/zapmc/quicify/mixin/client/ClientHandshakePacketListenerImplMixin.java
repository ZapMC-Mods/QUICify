package zapmc.quicify.mixin.client;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.util.CryptException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.Quicify;
import zapmc.quicify.quic.QuicLoginCrypto;
import zapmc.quicify.quic.QuicifyConnection;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.PublicKey;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {

    @Final
    @Shadow
    private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void quicify$rejectForeignCertificate(ClientboundHelloPacket packet, CallbackInfo ci) {
        QuicifyConnection quic = (QuicifyConnection) connection;
        if (!quic.quicify$isQuic()) {
            return;
        }
        byte[] packetKey = ((ClientboundHelloPacketAccessor) packet).quicify$rawPublicKey();
        if (!MessageDigest.isEqual(packetKey, quic.quicify$peerCertificateKey())) {
            Quicify.LOGGER.warn("QUIC hello key does not match the TLS peer certificate, possible MITM, disconnecting");
            connection.disconnect(Component.literal("QUIC certificate mismatch"));
            ci.cancel();
        }
    }

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/login/ClientboundHelloPacket;getPublicKey()Ljava/security/PublicKey;"
            )
    )
    private PublicKey quicify$certificateKeyInsteadOfRsa(ClientboundHelloPacket packet) throws CryptException {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            return QuicLoginCrypto.certificatePublicKey(((ClientboundHelloPacketAccessor) packet).quicify$rawPublicKey());
        }
        return packet.getPublicKey();
    }

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "NEW",
                    target = "(Ljavax/crypto/SecretKey;Ljava/security/PublicKey;[B)Lnet/minecraft/network/protocol/login/ServerboundKeyPacket;"
            )
    )
    private ServerboundKeyPacket quicify$cleartextKeyPacket(SecretKey secretKey, PublicKey publicKey, byte[] challenge) throws CryptException {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            return QuicLoginCrypto.cleartextKeyPacket(secretKey.getEncoded(), challenge);
        }
        return new ServerboundKeyPacket(secretKey, publicKey, challenge);
    }
}
