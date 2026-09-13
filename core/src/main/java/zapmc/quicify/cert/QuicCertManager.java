package zapmc.quicify.cert;

import zapmc.quicify.Quicify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

public final class QuicCertManager {

    private static final String KEY_FILE = "quic-key.pem";

    private static final String CERT_FILE = "quic-cert.pem";

    private static final long CERT_LIFETIME_DAYS = 3650;

    private static final byte[] PAIRING_PROBE = "quicify-key-cert-pairing-probe".getBytes(StandardCharsets.US_ASCII);

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    private QuicCertManager(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public static QuicCertManager loadOrGenerate(Path directory) throws GeneralSecurityException, IOException {
        Path keyPath = directory.resolve(KEY_FILE);
        Path certPath = directory.resolve(CERT_FILE);

        if (Files.exists(keyPath) && Files.exists(certPath)) {
            try {
                return load(keyPath, certPath);
            } catch (GeneralSecurityException | IOException | RuntimeException e) {
                Quicify.LOGGER.warn("QUIC identity at {} could not be read ({}), regenerating it; clients that already trusted this server will see a new identity", keyPath, e.toString());
            }
        }
        return generateAndStore(directory, keyPath, certPath);
    }

    private static QuicCertManager load(Path keyPath, Path certPath) throws GeneralSecurityException, IOException {
        byte[] keyDer = pemDecode(Files.readString(keyPath, StandardCharsets.US_ASCII), "PRIVATE KEY");
        byte[] certDer = pemDecode(Files.readString(certPath, StandardCharsets.US_ASCII), "CERTIFICATE");

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyDer));
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(new java.io.ByteArrayInputStream(certDer));
        requireMatchingPair(privateKey, certificate);
        return new QuicCertManager(privateKey, certificate);
    }

    private static void requireMatchingPair(PrivateKey privateKey, X509Certificate certificate) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(PAIRING_PROBE);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(PAIRING_PROBE);
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("QUIC private key does not correspond to the stored certificate");
        }
    }

    private static QuicCertManager generateAndStore(Path directory, Path keyPath, Path certPath) throws GeneralSecurityException, IOException {
        KeyPair keyPair = SelfSignedCertGenerator.generateKeyPair();
        Instant now = Instant.now();
        X509Certificate certificate = SelfSignedCertGenerator.generate(keyPair, "quicify", now.minus(1, ChronoUnit.MINUTES), now.plus(CERT_LIFETIME_DAYS, ChronoUnit.DAYS));

        Files.createDirectories(directory);
        Path keyTmp = prepareTemp(keyPath, pemEncode("PRIVATE KEY", keyPair.getPrivate().getEncoded()), true);
        Path certTmp = prepareTemp(certPath, pemEncode("CERTIFICATE", certificate.getEncoded()), false);
        try {
            moveIntoPlace(keyTmp, keyPath);
            moveIntoPlace(certTmp, certPath);
        } finally {
            deleteQuietly(keyTmp);
            deleteQuietly(certTmp);
        }
        return new QuicCertManager(keyPair.getPrivate(), certificate);
    }

    private static Path prepareTemp(Path path, String content, boolean restrict) throws IOException {
        Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.writeString(tmp, content, StandardCharsets.US_ASCII);
        if (restrict) {
            restrictToOwner(tmp);
        }
        return tmp;
    }

    private static void deleteQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            Quicify.LOGGER.debug("Could not remove temporary file {}", tmp, e);
        }
    }

    private static void moveIntoPlace(Path tmp, Path path) throws IOException {
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrictToOwner(Path keyPath) {
        try {
            Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            return;
        } catch (UnsupportedOperationException _) {
        } catch (IOException _) {
            return;
        }
        try {
            AclFileAttributeView acl = Files.getFileAttributeView(keyPath, AclFileAttributeView.class);
            if (acl == null) {
                return;
            }
            AclEntry ownerOnly = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(keyPath))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            acl.setAcl(List.of(ownerOnly));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static String pemEncode(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private static byte[] pemDecode(String pem, String type) {
        String stripped = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public X509Certificate certificate() {
        return certificate;
    }
}
