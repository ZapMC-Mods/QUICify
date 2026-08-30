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
        return new QuicCertManager(privateKey, certificate);
    }

    private static QuicCertManager generateAndStore(Path directory, Path keyPath, Path certPath) throws GeneralSecurityException, IOException {
        KeyPair keyPair = SelfSignedCertGenerator.generateKeyPair();
        Instant now = Instant.now();
        X509Certificate certificate = SelfSignedCertGenerator.generate(keyPair, "quicify", now.minus(1, ChronoUnit.MINUTES), now.plus(CERT_LIFETIME_DAYS, ChronoUnit.DAYS));

        Files.createDirectories(directory);
        writeAtomically(keyPath, pemEncode("PRIVATE KEY", keyPair.getPrivate().getEncoded()), true);
        writeAtomically(certPath, pemEncode("CERTIFICATE", certificate.getEncoded()), false);
        return new QuicCertManager(keyPair.getPrivate(), certificate);
    }

    private static void writeAtomically(Path path, String content, boolean restrict) throws IOException {
        Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.US_ASCII);
            if (restrict) {
                restrictToOwner(tmp);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
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
