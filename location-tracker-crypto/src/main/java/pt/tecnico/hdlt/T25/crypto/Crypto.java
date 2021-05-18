package pt.tecnico.hdlt.T25.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Crypto {

    public static byte[] getSHA256Hash(String input) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        return hash;
    }

    public static RSAPrivateKey getPriv(String filename, String keyId, String password) throws GeneralSecurityException {

        try {
            String keystorePath = "resources/keys/" + filename;
            File keystoreFile = new File(keystorePath);
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            if (keystoreFile.exists()) {
                keyStore.load(new FileInputStream(keystoreFile), password.toCharArray());
            } else {
                keyStore.load(null, null);
                keyStore.store(new FileOutputStream(keystoreFile), password.toCharArray());
            }

            return (RSAPrivateKey) keyStore.getKey(keyId, password.toCharArray());
        } catch (IOException e) {
            System.out.println("Failed to get private key.");
            return null;
        }
    }

    public static RSAPublicKey getPub(String filename) throws GeneralSecurityException {

        try {
            File file = new File("resources/keys/" + filename);
            String absolutePath = file.getAbsolutePath();
            byte[] keyBytes = Files.readAllBytes(Paths.get(absolutePath));

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (IOException e) {
            System.out.println("Failed to get public key.");
            return null;
        }
    }

    public static String encryptRSA(String plainText, PublicKey publicKey) throws GeneralSecurityException {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] cipherText = encryptCipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(cipherText);
    }

    public static String sign(String plainText, PrivateKey privateKey) {
        try {
            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(privateKey);
            privateSignature.update(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] signature = privateSignature.sign();

            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            System.out.println("Failed to sign message.");
        }
        return null;
    }

    public static boolean verify(String plainText, String signature, PublicKey publicKey) {
        try {
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(publicKey);
            publicSignature.update(plainText.getBytes(UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(signature);

            return publicSignature.verify(signatureBytes);
        } catch (Exception e) {
            System.out.println("Failed to verify message.");
        }

        return false;
    }

    public static String decryptRSA(String cipherText, PrivateKey privateKey) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(cipherText);

        Cipher decriptCipher = Cipher.getInstance("RSA");
        decriptCipher.init(Cipher.DECRYPT_MODE, privateKey);

        return new String(decriptCipher.doFinal(bytes), UTF_8);
    }

    public static String encryptAES(String value) {
        try {
            SecretKeySpec skeySpec = getAESKey("aes.key");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }));

            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static String encryptAES(SecretKeySpec secretKeySpec, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }));

            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static SecretKeySpec getAESKey(String keyPath) throws IOException {
        File file = new File("resources/keys/" + keyPath);
        String absolutePath = file.getAbsolutePath();
        FileInputStream fis = new FileInputStream(absolutePath);
        byte[] encoded = new byte[fis.available()];
        fis.read(encoded);
        fis.close();

        return new SecretKeySpec(encoded, 0, 16, "AES");
    }

    public static String decryptAES(String encrypted) {
        try {
            SecretKeySpec skeySpec = getAESKey("aes.key");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }));

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));

            return new String(original);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public static String decryptAES(SecretKeySpec secretKeySpec, String encrypted) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }));

        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));

        return new String(original);
    }

    public static SecretKeySpec decryptKeyWithRSA(String key, PrivateKey privateKey) throws GeneralSecurityException {
        String keyContent = Crypto.decryptRSA(key, privateKey);
        if (keyContent == null) return null;
        byte[] decodedKey = Base64.getDecoder().decode(keyContent);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static byte[] generateSecretKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator.generateKey().getEncoded();
    }
}
