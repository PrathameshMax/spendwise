package com.spendwise.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter applying {@link AesGcmFieldEncryptor} transparently:
 * {@code @Convert(converter = EncryptedStringConverter.class)} on an entity field
 * stores ciphertext in the column and returns plaintext to the code. Hibernate
 * obtains converters from Spring (Boot wires its SpringBeanContainer), so the
 * encryptor bean is constructor-injected.
 *
 * <p>Encrypted columns cannot be searched or uniquely indexed (random IV) — use
 * it for display-only PII such as names or account numbers, not lookup keys.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmFieldEncryptor encryptor;

    public EncryptedStringConverter(AesGcmFieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
