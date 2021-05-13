package pt.tecnico.hdlt.T25.crypto;

import sun.security.x509.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class RSAKeyGenerator {

    private static final String certificateAlgorithm = "SHA256withRSA";

    public static void main(String[] args) throws Exception {

        String clientId = "client";
        String password = "pw";
        //for (int i = 0; i < 75; i++) {
        //    write(clientId + i, password);
        //}

        //write("ha", password);

        //write("server", password);

        System.out.println("Done.");
    }

    public static void write(String filename, String password) throws Exception {
        // get an RSA private key
        System.out.println("Generating RSA key ...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keys = keyGen.generateKeyPair();

        // Generate keystore if not exists
        String keystorePath = "resources/keys/" + filename + ".jks";
        File keystoreFile = new File(keystorePath);
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (keystoreFile.exists()) {
            keyStore.load(new FileInputStream(keystoreFile), password.toCharArray());
        } else {
            keyStore.load(null, null);
            keyStore.store(new FileOutputStream(keystoreFile), password.toCharArray());
        }

        // Store private key and certificate in the keystore
        X509Certificate[] chain = {generateCertificate(filename, keys)};
        keyStore.setKeyEntry(filename, keys.getPrivate(), password.toCharArray(), chain);
        keyStore.store(new FileOutputStream(keystoreFile), password.toCharArray());

        // Store certificate in a common directory (certificates are shared between users)
        String certificatePath = "resources/keys/" + filename + ".crt";
        System.out.println("Writing Certificate to '" + certificatePath + "' ...");
        FileOutputStream certFos = new FileOutputStream(certificatePath);
        certFos.write(chain[0].getEncoded());
        certFos.close();
    }

    private static X509Certificate generateCertificate(String clientId, KeyPair keys) throws Exception {
        String name = "cn=" + clientId, ca = "cn=IST";

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
        certificate.sign(keys.getPrivate(), certificateAlgorithm);

        sigAlgId = (AlgorithmId) certificate.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, sigAlgId);
        certificate = new X509CertImpl(info);
        certificate.sign(keys.getPrivate(), certificateAlgorithm);

        return certificate;
    }

}
