package zapmc.quicify.cert;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class SelfSignedCertGenerator {

    private static final String OID_SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2";
    private static final String OID_EC_PUBLIC_KEY = "1.2.840.10045.2.1";
    private static final String OID_P256 = "1.2.840.10045.3.1.7";
    private static final String OID_CN = "2.5.4.3";

    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return generator.generateKeyPair();
    }

    public static X509Certificate generate(KeyPair keyPair, String commonName, Instant notBefore, Instant notAfter) throws GeneralSecurityException {
        byte[] tbsCertificate = buildTbsCertificate(keyPair, commonName, notBefore, notAfter);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(tbsCertificate);
        byte[] signature = signer.sign();

        byte[] certificateDer = Der.sequence(tbsCertificate, signatureAlgorithm(), Der.bitString(signature));

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificateDer));
    }

    private static byte[] buildTbsCertificate(KeyPair keyPair, String commonName, Instant notBefore, Instant notAfter) {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        byte[] uncompressedPoint = Der.concat(new byte[]{0x04}, toFixedWidth(publicKey.getW().getAffineX()), toFixedWidth(publicKey.getW().getAffineY()));

        byte[] name = Der.sequence(Der.set(Der.sequence(Der.oid(OID_CN), Der.utf8String(commonName))));
        byte[] subjectPublicKeyInfo = Der.sequence(Der.sequence(Der.oid(OID_EC_PUBLIC_KEY), Der.oid(OID_P256)), Der.bitString(uncompressedPoint));
        byte[] validity = Der.sequence(Der.utcTime(UTC_TIME.format(notBefore)), Der.utcTime(UTC_TIME.format(notAfter)));

        byte[] serial = new byte[8];
        new SecureRandom().nextBytes(serial);

        return Der.sequence(Der.explicit0(Der.integer(new byte[]{2})), Der.integer(serial), signatureAlgorithm(), name, validity, name, subjectPublicKeyInfo);
    }

    private static byte[] signatureAlgorithm() {
        return Der.sequence(Der.oid(OID_SHA256_WITH_ECDSA));
    }

    private static byte[] toFixedWidth(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }
}
