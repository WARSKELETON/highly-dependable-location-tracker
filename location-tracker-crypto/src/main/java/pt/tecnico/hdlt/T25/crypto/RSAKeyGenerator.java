package pt.tecnico.hdlt.T25.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class RSAKeyGenerator {

    public static void main(String[] args) throws Exception {
        //Generate server keys
        for (int i = 0; i < 4; i++)
            write("server" + i + ".jks", "server" + i + "-pub.key", "server" + i, "server" + i);

        //Generate client keys
        for (int i = 0; i < 75; i++)
            write("client" + i + ".jks", "client" + i + "-pub.key", "client" + i, "client" + i);

        //Generate HA client keys
        write("ha.jks", "ha-pub.key", "ha", "ha");

        System.out.println("Done.");
    }

    public static void write(String keystoreFilePath, String publicKeyPath, String keyId, String password) throws Exception {
        // Generate keystore if not exists
        String keystorePath = "resources/keys/" + keystoreFilePath;
        File keystoreFile = new File(keystorePath);
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (keystoreFile.exists()) {
            keyStore.load(new FileInputStream(keystoreFile), password.toCharArray());
        } else {
            keyStore.load(null, null);
            keyStore.store(new FileOutputStream(keystoreFile), password.toCharArray());
        }

        // get an RSA private key
        System.out.println("Generating RSA key ...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keys = keyGen.generateKeyPair();

        System.out.println("Storing in keystore..." );
        // Store private key and certificate in the keystore
        X509Certificate[] chain = generateCertificate(keyId, keys);
        keyStore.setKeyEntry(keyId, keys.getPrivate(), password.toCharArray(), chain);
        keyStore.store(new FileOutputStream(keystoreFile), password.toCharArray());

        // Save public key
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();

        // Store public key in file
        String publicKeyFilePath = "resources/keys/" + publicKeyPath;
        System.out.println("Writing Public key to '" + publicKeyFilePath + "' ..." );
        FileOutputStream pubFos = new FileOutputStream(publicKeyFilePath);
        pubFos.write(pubKeyEncoded);
        pubFos.close();

        System.out.println("...Done");
    }

    private static X509Certificate[] generateCertificate(String clientId, KeyPair keys) throws OperatorCreationException, CertificateException {
        Date from = new Date();
        Date to = new Date(from.getTime() + 365 * 1000L * 24L * 60L * 60L);
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name("CN=" + clientId);

        Security.addProvider(new BouncyCastleProvider());
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate());
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                owner,
                serialNumber,
                from,
                to,
                owner,
                keys.getPublic()
        );

        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certificateBuilder.build(contentSigner));
        return new X509Certificate[] { certificate };
    }

}
