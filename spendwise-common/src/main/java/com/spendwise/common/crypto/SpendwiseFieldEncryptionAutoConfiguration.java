package com.spendwise.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link AesGcmFieldEncryptor} when the service is given a key
 * ({@code spendwise.security.field-encryption.key}, sourced from Vault).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spendwise.security.field-encryption", name = "key")
public class SpendwiseFieldEncryptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AesGcmFieldEncryptor aesGcmFieldEncryptor(@Value("${spendwise.security.field-encryption.key}") String key) {
        return new AesGcmFieldEncryptor(key);
    }
}
