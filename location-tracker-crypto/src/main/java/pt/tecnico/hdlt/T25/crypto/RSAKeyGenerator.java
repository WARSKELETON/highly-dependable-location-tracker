package pt.tecnico.hdlt.T25.crypto;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

public class RSAKeyGenerator {

    public static void main(String[] args) throws Exception {

        for (int i = 0; i < 4; i++) {
            write("server" + i + "-priv.key", "server" + i + "-pub.key");
        }

        System.out.println("Done.");
    }

    public static void write(String privateKeyPath, String publicKeyPath) throws GeneralSecurityException, IOException {
        // get an AES private key
        System.out.println("Generating RSA key ..." );
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keys = keyGen.generateKeyPair();

        System.out.println("Private Key:");
        PrivateKey privKey = keys.getPrivate();
        byte[] privKeyEncoded = privKey.getEncoded();

        System.out.println("Public Key:");
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();

        System.out.println("Writing Private key to '" + privateKeyPath + "' ..." );
        FileOutputStream privFos = new FileOutputStream(privateKeyPath);
        privFos.write(privKeyEncoded);
        privFos.close();

        System.out.println("Writing Pubic key to '" + publicKeyPath + "' ..." );
        FileOutputStream pubFos = new FileOutputStream(publicKeyPath);
        pubFos.write(pubKeyEncoded);
        pubFos.close();
    }

}
