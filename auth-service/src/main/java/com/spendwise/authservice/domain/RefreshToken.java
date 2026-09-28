package com.spendwise.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per refresh token ever issued, keyed by the token's JWT {@code jti}
 * claim. Backs rotation-with-reuse-detection:
 * {@link com.spendwise.authservice.security.TokenService#refresh} marks a token
 * revoked the moment it is exchanged for a new pair, so presenting an
 * already-rotated-out refresh token (a signal of token theft/replay) is rejected
 * even though the JWT itself is still cryptographically valid and unexpired.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "replaced_by_jti")
    private UUID replacedByJti;

    protected RefreshToken() {
        // required by JPA
    }

    public RefreshToken(UUID id, UUID credentialId, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.credentialId = credentialId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public UUID getReplacedByJti() {
        return replacedByJti;
    }

    public void revoke(UUID replacedByJti) {
        this.revoked = true;
        this.replacedByJti = replacedByJti;
    }
}
