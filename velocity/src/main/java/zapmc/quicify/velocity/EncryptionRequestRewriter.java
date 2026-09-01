package zapmc.quicify.velocity;

import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.packet.EncryptionRequestPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.jspecify.annotations.Nullable;

import java.security.PublicKey;
import java.security.SecureRandom;

public final class EncryptionRequestRewriter extends ChannelOutboundHandlerAdapter {

    public static final String NAME = "quicify_hello";

    private static final int CHALLENGE_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PublicKey certificateKey;

    private volatile byte @Nullable [] challenge;

    public EncryptionRequestRewriter(PublicKey certificateKey) {
        this.certificateKey = certificateKey;
    }

    public static @Nullable EncryptionRequestRewriter of(Channel channel) {
        return channel.pipeline().get(EncryptionRequestRewriter.class);
    }

    public static void install(Channel channel, PublicKey certificateKey) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(Connections.MINECRAFT_ENCODER) != null && pipeline.get(NAME) == null) {
            pipeline.addAfter(Connections.MINECRAFT_ENCODER, NAME, new EncryptionRequestRewriter(certificateKey));
        }
    }

    public byte @Nullable [] challenge() {
        return challenge;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof EncryptionRequestPacket request) {
            byte[] issued = new byte[CHALLENGE_LENGTH];
            RANDOM.nextBytes(issued);
            challenge = issued;
            request.setPublicKey(certificateKey.getEncoded());
            request.setVerifyToken(issued);
        }
        ctx.write(msg, promise);
    }
}
