package zapmc.quicify.mixin;

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zapmc.quicify.quic.QuicifyConnection;
import zapmc.quicify.quic.zstd.QuicCompression;

import javax.crypto.Cipher;
import java.net.SocketAddress;
import java.security.PublicKey;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements QuicifyConnection {

    @Shadow
    @Nullable
    private Channel channel;

    @Shadow
    @Nullable
    private BandwidthDebugMonitor bandwidthDebugMonitor;

    @Override
    public @Nullable BandwidthDebugMonitor quicify$bandwidthDebugMonitor() {
        return bandwidthDebugMonitor;
    }

    @Unique
    @Nullable
    private volatile PublicKey quicify$localCertificateKey;

    @Unique
    private volatile byte @Nullable [] quicify$peerCertificateKey;

    @Unique
    @Nullable
    private volatile QuicChannel quicify$quicChannel;

    @Override
    public @Nullable QuicChannel quicify$quicChannel() {
        return quicify$quicChannel;
    }

    @Override
    public boolean quicify$isQuic() {
        return quicify$localCertificateKey != null || quicify$quicChannel != null;
    }

    @Override
    public void quicify$markQuicServer(QuicChannel channel, PublicKey localCertificateKey) {
        this.quicify$quicChannel = channel;
        this.quicify$localCertificateKey = localCertificateKey;
    }

    @Override
    public void quicify$markQuicClient(QuicChannel channel, byte[] peerCertificateKey) {
        this.quicify$quicChannel = channel;
        this.quicify$peerCertificateKey = peerCertificateKey;
    }

    @Override
    public @Nullable PublicKey quicify$localCertificateKey() {
        return quicify$localCertificateKey;
    }

    @Override
    public byte @Nullable [] quicify$peerCertificateKey() {
        return quicify$peerCertificateKey;
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void quicify$skipAesEncryption(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (quicify$isQuic()) {
            ci.cancel();
        }
    }

    @Inject(method = "setupCompression", at = @At("HEAD"), cancellable = true)
    private void quicify$useZstdCompression(int threshold, boolean validateDecompressed, CallbackInfo ci) {
        Channel master = channel;
        if (!quicify$isQuic() || master == null) {
            return;
        }
        QuicCompression.setup(quicify$quicChannel, master, threshold, validateDecompressed);
        ci.cancel();
    }

    @Inject(method = "getLoggableAddress", at = @At("HEAD"), cancellable = true)
    private void quicify$loggableUdpAddress(boolean logIPs, CallbackInfoReturnable<String> cir) {
        QuicChannel channel = quicify$quicChannel;
        if (channel != null) {
            SocketAddress remote = channel.remoteSocketAddress();
            cir.setReturnValue(logIPs ? String.valueOf(remote) : "IP hidden");
        }
    }

    @Inject(method = "getRemoteAddress", at = @At("HEAD"), cancellable = true)
    private void quicify$realRemoteAddress(CallbackInfoReturnable<SocketAddress> cir) {
        QuicChannel channel = quicify$quicChannel;
        if (channel != null) {
            cir.setReturnValue(channel.remoteSocketAddress());
        }
    }
}
