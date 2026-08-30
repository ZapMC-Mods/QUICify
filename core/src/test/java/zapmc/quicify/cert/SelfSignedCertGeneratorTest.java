package zapmc.quicify.cert;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SelfSignedCertGeneratorTest {

    @Test
    void generatesParseableVerifiableCertificate() throws Exception {
        KeyPair keyPair = SelfSignedCertGenerator.generateKeyPair();
        Instant now = Instant.now();
        X509Certificate certificate = SelfSignedCertGenerator.generate(keyPair, "quicify", now.minus(1, ChronoUnit.MINUTES), now.plus(365, ChronoUnit.DAYS));

        assertEquals("CN=quicify", certificate.getSubjectX500Principal().getName());
        assertEquals(certificate.getSubjectX500Principal(), certificate.getIssuerX500Principal());
        assertDoesNotThrow(() -> certificate.verify(keyPair.getPublic()));
        assertDoesNotThrow(() -> certificate.checkValidity());
        assertNotNull(certificate.getPublicKey().getEncoded());
    }
}
