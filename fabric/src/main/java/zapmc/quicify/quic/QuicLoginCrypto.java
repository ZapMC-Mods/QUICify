package zapmc.quicify.quic;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public final class QuicLoginCrypto {

    public static PublicKey certificatePublicKey(byte[] certificatePublicKeyDer) throws CryptException {
        try {
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(certificatePublicKeyDer));
        } catch (Exception e) {
            throw new CryptException(e);
        }
    }

    public static String sessionDigest(String serverId, byte[] certificatePublicKeyDer, byte[] sharedSecret) throws CryptException {
        try {
            PublicKey certificateKey = certificatePublicKey(certificatePublicKeyDer);
            SecretKey secretKey = new SecretKeySpec(sharedSecret, "AES");
            return new BigInteger(Crypt.digestData(serverId, certificateKey, secretKey)).toString(16);
        } catch (CryptException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptException(e);
        }
    }

    public static ServerboundKeyPacket cleartextKeyPacket(byte[] sharedSecret, byte[] challenge) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByteArray(sharedSecret);
            buffer.writeByteArray(challenge);
            return ServerboundKeyPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
