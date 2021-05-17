package pt.tecnico.hdlt.T25.crypto;

import sun.security.x509.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
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
        X509Certificate[] chain = {generateCertificate(keyId, keys)};
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

    private static X509Certificate generateCertificate(String clientId, KeyPair keys) throws Exception {
        String name = "CN=" + clientId, ca = "CN=IST";

        X509CertInfo info = new X509CertInfo();
        Date from = new Date();
        Date to = new Date(from.getTime() + 365 * 1000L * 24L * 60L * 60L);

        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        AlgorithmId sigAlgId = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
        info.set(X509CertInfo.SUBJECT, new X500Name(name));
        info.set(X509CertInfo.ISSUER, new X500Name(ca));
        info.set(X509CertInfo.KEY, new CertificateX509Key(keys.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(sigAlgId));

        // Generate certificate
        X509CertImpl certificate = new X509CertImpl(info);
        certificate.sign(keys.getPrivate(), "SHA256withRSA");

        sigAlgId = (AlgorithmId) certificate.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, sigAlgId);
        certificate = new X509CertImpl(info);
        certificate.sign(keys.getPrivate(), "SHA256withRSA");

        return certificate;
    }

}
