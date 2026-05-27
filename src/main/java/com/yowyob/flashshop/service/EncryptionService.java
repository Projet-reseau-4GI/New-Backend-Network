package com.yowyob.flashshop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Service for data encryption and decryption using AES/GCM.
 * Provides secure handling of sensitive document data.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secret_key;
    private final SecureRandom secure_random = new SecureRandom();

    /**
     * Initializes the encryption service with a secret key.
     *
     * @param key the secret key string (will be padded/truncated to 32 bytes for
     *            AES-256)
     */
    public EncryptionService(@Value("${encryption.secret-key:Ks8Jp2Lm4nQ7Rs9Tv1Wx3Yz5Ab7Cd9Ef}") String key) {
        byte[] key_bytes = Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), 32);
        this.secret_key = new SecretKeySpec(key_bytes, "AES");
    }

    /**
     * Encrypts the given plaintext byte array.
     *
     * @param plaintext the data to encrypt
     * @return the encrypted data including the IV
     * @throws RuntimeException if encryption fails
     */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secure_random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secret_key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            log.error("Encryption failure: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the given encrypted byte array.
     *
     * @param encrypted the data to decrypt (including the IV at the beginning)
     * @return the decrypted plaintext data
     * @throws RuntimeException if decryption fails
     */
    public byte[] decrypt(byte[] encrypted) {
        try {
            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secret_key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            log.error("Decryption failure: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
