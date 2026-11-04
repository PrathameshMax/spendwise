package com.spendwise.common.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmFieldEncryptorTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        AesGcmFieldEncryptor encryptor = new AesGcmFieldEncryptor(randomKey());

        String stored = encryptor.encrypt("Alice Fernandes");

        assertThat(stored).startsWith("v1:").doesNotContain("Alice");
        assertThat(encryptor.decrypt(stored)).isEqualTo("Alice Fernandes");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        AesGcmFieldEncryptor encryptor = new AesGcmFieldEncryptor(randomKey());

        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    void wrongKeyOrTamperedValueFailsInsteadOfReturningGarbage() {
        String stored = new AesGcmFieldEncryptor(randomKey()).encrypt("secret");

        assertThatThrownBy(() -> new AesGcmFieldEncryptor(randomKey()).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyPlaintextRowsAreReturnedAsIs() {
        assertThat(new AesGcmFieldEncryptor(randomKey()).decrypt("Legacy Name")).isEqualTo("Legacy Name");
    }

    @Test
    void rejectsKeysThatAreNot256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmFieldEncryptor(shortKey)).isInstanceOf(IllegalArgumentException.class);
    }
}
