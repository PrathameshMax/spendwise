package com.spendwise.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level encryption for the most sensitive columns: AES-256-GCM
 * (authenticated — a tampered ciphertext fails to decrypt instead of returning
 * garbage), a fresh random 96-bit IV per value, stored as
 * {@code v1:base64(iv || ciphertext || tag)}.
 *
 * <p>The key is 32 random bytes, Base64-encoded, read from Vault
 * ({@code secret/<service>} → {@code spendwise.security.field-encryption.key}),
 * never from Git. The {@code v1:} prefix versions the format for key rotation;
 * a value without it is treated as legacy plaintext, so the column can be
 * migrated in place.
 */
public final class AesGcmFieldEncryptor {

    private static final String FORMAT_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmFieldEncryptor(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("spendwise.security.field-encryption.key must be set");
        }
        byte[] rawKey = Base64.getDecoder().decode(base64Key.trim());
        if (rawKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("Field encryption key must be 256 bits (32 bytes), got "
                    + rawKey.length + " bytes");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(FORMAT_PREFIX)) {
            return stored;
        }
        byte[] payload = Base64.getDecoder().decode(stored.substring(FORMAT_PREFIX.length()));
        if (payload.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Encrypted field payload is truncated");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field decryption failed (wrong key or tampered value)", e);
        }
    }
}
