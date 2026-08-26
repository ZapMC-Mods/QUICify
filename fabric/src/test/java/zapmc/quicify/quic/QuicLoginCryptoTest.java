package zapmc.quicify.quic;

import net.minecraft.util.Crypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zapmc.quicify.cert.QuicCertManager;
import zapmc.quicify.cert.SelfSignedCertGenerator;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuicLoginCryptoTest {

    private static String serverPath(QuicCertManager identity, SecretKey sharedSecret) throws Exception {
        PublicKey localCertificateKey = identity.certificate().getPublicKey();
        return new BigInteger(Crypt.digestData("", localCertificateKey, sharedSecret)).toString(16);
    }

    private static String clientPath(QuicCertManager identity, SecretKey sharedSecret) throws Exception {
        byte[] der = identity.certificate().getPublicKey().getEncoded();
        return new BigInteger(Crypt.digestData("", QuicLoginCrypto.certificatePublicKey(der), sharedSecret)).toString(16);
    }

    private static String probePath(QuicCertManager identity, SecretKey sharedSecret) throws Exception {
        byte[] der = identity.certificate().getPublicKey().getEncoded();
        return QuicLoginCrypto.sessionDigest("", der, sharedSecret.getEncoded());
    }

    @Test
    void certificateKeyDerRoundTripsUnchanged() throws Exception {
        byte[] der = SelfSignedCertGenerator.generateKeyPair().getPublic().getEncoded();

        assertArrayEquals(der, QuicLoginCrypto.certificatePublicKey(der).getEncoded());
    }

    @Test
    void clientAndServerComputeTheSameDigest() throws Exception {
        KeyPair certificateKeyPair = SelfSignedCertGenerator.generateKeyPair();
        byte[] der = certificateKeyPair.getPublic().getEncoded();
        SecretKey sharedSecret = Crypt.generateSecretKey();

        String serverDigest = new BigInteger(Crypt.digestData("", certificateKeyPair.getPublic(), sharedSecret)).toString(16);
        String clientDigest = QuicLoginCrypto.sessionDigest("", der, sharedSecret.getEncoded());

        assertEquals(serverDigest, clientDigest);
    }

    @Test
    void allThreePathsAgreeOnAPersistedIdentity(@TempDir Path directory) throws Exception {
        QuicCertManager identity = QuicCertManager.loadOrGenerate(directory);
        SecretKey sharedSecret = Crypt.generateSecretKey();

        assertEquals(serverPath(identity, sharedSecret), clientPath(identity, sharedSecret));
        assertEquals(serverPath(identity, sharedSecret), probePath(identity, sharedSecret));
    }

    @Test
    void theDigestSurvivesAServerRestart(@TempDir Path directory) throws Exception {
        QuicCertManager beforeRestart = QuicCertManager.loadOrGenerate(directory);
        QuicCertManager afterRestart = QuicCertManager.loadOrGenerate(directory);
        SecretKey sharedSecret = Crypt.generateSecretKey();

        assertEquals(serverPath(beforeRestart, sharedSecret), serverPath(afterRestart, sharedSecret));
        assertEquals(serverPath(beforeRestart, sharedSecret), clientPath(afterRestart, sharedSecret));
    }
}
