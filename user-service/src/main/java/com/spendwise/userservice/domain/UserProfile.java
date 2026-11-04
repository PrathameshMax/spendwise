package com.spendwise.userservice.domain;

import com.spendwise.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A SpendWise profile. Its id is the Keycloak user id (the access token's
 * {@code sub}), assigned at first login — so "whose profile is this" is answered
 * by the token, never by a client-supplied id. {@code fullName} is PII and is
 * stored AES-GCM encrypted ({@link EncryptedStringConverter}); {@code email} stays
 * plaintext because it is a unique lookup key.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "preferred_currency", nullable = false)
    private String preferredCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(UUID id, String email, String fullName, String preferredCurrency) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.preferredCurrency = preferredCurrency;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String fullName, String preferredCurrency) {
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (preferredCurrency != null) {
            this.preferredCurrency = preferredCurrency;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
