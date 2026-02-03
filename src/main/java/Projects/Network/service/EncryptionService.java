package Projects.Network.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
@Slf4j
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${encryption.secret-key:Ks8Jp2Lm4nQ7Rs9Tv1Wx3Yz5Ab7Cd9Ef}") String key) {
        byte[] keyBytes = Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), 32);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        try {
            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
