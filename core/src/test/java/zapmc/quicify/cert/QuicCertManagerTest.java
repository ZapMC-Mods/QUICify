package zapmc.quicify.cert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class QuicCertManagerTest {

    private static final String KEY_FILE = "quic-key.pem";

    private static final String CERT_FILE = "quic-cert.pem";

    @Test
    void writesBothFilesOnFirstStart(@TempDir Path directory) throws Exception {
        QuicCertManager identity = QuicCertManager.loadOrGenerate(directory);

        assertTrue(Files.exists(directory.resolve(KEY_FILE)));
        assertTrue(Files.exists(directory.resolve(CERT_FILE)));
        assertTrue(Files.readString(directory.resolve(KEY_FILE)).startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(Files.readString(directory.resolve(CERT_FILE)).startsWith("-----BEGIN CERTIFICATE-----"));
        identity.certificate().verify(identity.certificate().getPublicKey());
    }

    @Test
    void restrictsThePrivateKeyToItsOwner(@TempDir Path directory) throws Exception {
        QuicCertManager.loadOrGenerate(directory);
        Path keyPath = directory.resolve(KEY_FILE);

        PosixFileAttributeView posix = Files.getFileAttributeView(keyPath, PosixFileAttributeView.class);
        if (posix != null) {
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(keyPath));
            return;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(keyPath, AclFileAttributeView.class);
        assumeTrue(acl != null, "filesystem supports neither POSIX permissions nor ACLs");
        UserPrincipal owner = Files.getOwner(keyPath);
        for (AclEntry entry : acl.getAcl()) {
            assertEquals(owner, entry.principal(), "private key is reachable by " + entry.principal().getName());
        }
    }

    @Test
    void keepsTheSameIdentityAcrossRestarts(@TempDir Path directory) throws Exception {
        QuicCertManager first = QuicCertManager.loadOrGenerate(directory);
        QuicCertManager second = QuicCertManager.loadOrGenerate(directory);

        assertArrayEquals(first.certificate().getPublicKey().getEncoded(), second.certificate().getPublicKey().getEncoded());
        assertArrayEquals(first.certificate().getEncoded(), second.certificate().getEncoded());
        assertArrayEquals(first.privateKey().getEncoded(), second.privateKey().getEncoded());
    }

    @Test
    void regeneratesWhenTheCertificateIsMissing(@TempDir Path directory) throws Exception {
        QuicCertManager first = QuicCertManager.loadOrGenerate(directory);
        Files.delete(directory.resolve(CERT_FILE));

        QuicCertManager second = QuicCertManager.loadOrGenerate(directory);

        assertTrue(Files.exists(directory.resolve(CERT_FILE)));
        assertFalse(java.util.Arrays.equals(first.certificate().getPublicKey().getEncoded(), second.certificate().getPublicKey().getEncoded()));
    }

    @Test
    void regeneratesFromATruncatedKey(@TempDir Path directory) throws Exception {
        QuicCertManager first = QuicCertManager.loadOrGenerate(directory);
        String key = Files.readString(directory.resolve(KEY_FILE), StandardCharsets.US_ASCII);
        Files.writeString(directory.resolve(KEY_FILE), key.substring(0, key.length() / 2), StandardCharsets.US_ASCII);

        QuicCertManager second = assertDoesNotThrow(() -> QuicCertManager.loadOrGenerate(directory));

        assertFalse(java.util.Arrays.equals(first.certificate().getPublicKey().getEncoded(), second.certificate().getPublicKey().getEncoded()));
    }

    @Test
    void regeneratesFromAKeyThatIsNotPemAtAll(@TempDir Path directory) throws Exception {
        QuicCertManager.loadOrGenerate(directory);
        Files.writeString(directory.resolve(KEY_FILE), "this is not a PEM file!", StandardCharsets.US_ASCII);

        QuicCertManager reloaded = assertDoesNotThrow(() -> QuicCertManager.loadOrGenerate(directory));

        reloaded.certificate().verify(reloaded.certificate().getPublicKey());
    }

    @Test
    void regeneratesFromAnEmptyKeyFile(@TempDir Path directory) throws Exception {
        QuicCertManager.loadOrGenerate(directory);
        Files.writeString(directory.resolve(KEY_FILE), "", StandardCharsets.US_ASCII);

        QuicCertManager reloaded = assertDoesNotThrow(() -> QuicCertManager.loadOrGenerate(directory));

        reloaded.certificate().verify(reloaded.certificate().getPublicKey());
    }

    @Test
    void regeneratesFromAMismatchedKeyAndCertificatePair(@TempDir Path directory) throws Exception {
        QuicCertManager first = QuicCertManager.loadOrGenerate(directory);
        String keyFromFirstGeneration = Files.readString(directory.resolve(KEY_FILE), StandardCharsets.US_ASCII);
        Files.delete(directory.resolve(KEY_FILE));
        Files.delete(directory.resolve(CERT_FILE));
        QuicCertManager second = QuicCertManager.loadOrGenerate(directory);

        // Simulate a crash between the two Files.move calls in generateAndStore: the key on
        // disk is left over from one generation while the certificate belongs to another. Both
        // files are individually well-formed PEM, so only a correspondence check catches this.
        Files.writeString(directory.resolve(KEY_FILE), keyFromFirstGeneration, StandardCharsets.US_ASCII);

        QuicCertManager third = assertDoesNotThrow(() -> QuicCertManager.loadOrGenerate(directory));

        assertArrayEquals(third.privateKey().getEncoded(), readPrivateKey(directory));
        assertMatchingPair(third);
        assertFalse(java.util.Arrays.equals(first.certificate().getEncoded(), third.certificate().getEncoded()));
        assertFalse(java.util.Arrays.equals(second.certificate().getEncoded(), third.certificate().getEncoded()));
    }

    private static byte[] readPrivateKey(Path directory) throws Exception {
        String pem = Files.readString(directory.resolve(KEY_FILE), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(pem))).getEncoded();
    }

    private static void assertMatchingPair(QuicCertManager identity) throws Exception {
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withECDSA");
        signer.initSign(identity.privateKey());
        signer.update("pairing-check".getBytes(StandardCharsets.US_ASCII));
        byte[] signature = signer.sign();

        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(identity.certificate().getPublicKey());
        verifier.update("pairing-check".getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(signature), "private key does not correspond to the certificate");
    }
}
