package zapmc.quicify.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.quic.QuicifyConnection;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Objects;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Unique
    private static final int AES_KEY_LENGTH = 16;

    @Unique
    private static final int QUICIFY_CHALLENGE_LENGTH = 16;

    @Unique
    private static final SecureRandom QUICIFY_CHALLENGE_RANDOM = new SecureRandom();

    @Final
    @Shadow
    private Connection connection;

    @Mutable
    @Final
    @Shadow
    private byte[] challenge;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void quicify$secureChallenge(MinecraftServer server, Connection connection, boolean transferred, CallbackInfo ci) {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            byte[] secureChallenge = new byte[QUICIFY_CHALLENGE_LENGTH];
            QUICIFY_CHALLENGE_RANDOM.nextBytes(secureChallenge);
            this.challenge = secureChallenge;
        }
    }

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/String;[B[BZ)Lnet/minecraft/network/protocol/login/ClientboundHelloPacket;"
            )
    )
    private ClientboundHelloPacket quicify$helloWithCertificateKey(String serverId, byte[] publicKey, byte[] challenge, boolean shouldAuthenticate) {
        QuicifyConnection quic = (QuicifyConnection) connection;
        if (quic.quicify$isQuic()) {
            PublicKey localCertificateKey = Objects.requireNonNull(quic.quicify$localCertificateKey(), "QUIC server connection has no local certificate key");
            return new ClientboundHelloPacket(serverId, localCertificateKey.getEncoded(), challenge, shouldAuthenticate);
        }
        return new ClientboundHelloPacket(serverId, publicKey, challenge, shouldAuthenticate);
    }

    @Redirect(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/login/ServerboundKeyPacket;isChallengeValid([BLjava/security/PrivateKey;)Z"
            )
    )
    private boolean quicify$validateChallenge(ServerboundKeyPacket packet, byte[] challenge, java.security.PrivateKey privateKey) {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            return MessageDigest.isEqual(this.challenge, ((ServerboundKeyPacketAccessor) packet).quicify$rawChallenge());
        }
        return packet.isChallengeValid(challenge, privateKey);
    }

    @Redirect(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/login/ServerboundKeyPacket;getSecretKey(Ljava/security/PrivateKey;)Ljavax/crypto/SecretKey;"
            )
    )
    private SecretKey quicify$cleartextSecretKey(ServerboundKeyPacket packet, java.security.PrivateKey privateKey) throws net.minecraft.util.CryptException {
        if (((QuicifyConnection) connection).quicify$isQuic()) {
            byte[] rawKeybytes = ((ServerboundKeyPacketAccessor) packet).quicify$rawKeybytes();
            if (rawKeybytes.length != AES_KEY_LENGTH) {
                throw new net.minecraft.util.CryptException(new IllegalArgumentException("expected a " + AES_KEY_LENGTH + " byte AES key, got " + rawKeybytes.length));
            }
            return new SecretKeySpec(rawKeybytes, "AES");
        }
        return packet.getSecretKey(privateKey);
    }

    @Redirect(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/security/KeyPair;getPublic()Ljava/security/PublicKey;"
            )
    )
    private PublicKey quicify$digestBindsCertificateKey(KeyPair keyPair) {
        QuicifyConnection quic = (QuicifyConnection) connection;
        if (quic.quicify$isQuic()) {
            return Objects.requireNonNull(quic.quicify$localCertificateKey(), "QUIC server connection has no local certificate key");
        }
        return keyPair.getPublic();
    }
}
