package com.spendwise.authservice.security;

import com.spendwise.authservice.domain.Credential;
import com.spendwise.authservice.domain.RefreshToken;
import com.spendwise.authservice.domain.RefreshTokenRepository;
import com.spendwise.authservice.exception.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Mints and rotates the access/refresh token pairs auth-service issues as this
 * platform's OAuth2 Authorization Server (Milestone 6). Access tokens are
 * short-lived, stateless JWTs the API Gateway validates against
 * {@code /oauth2/jwks}; refresh tokens are also signed JWTs, but additionally
 * tracked in {@link RefreshTokenRepository} by their {@code jti} claim so a
 * rotated-out token can be detected and rejected even before its own expiry.
 */
@Component
public class TokenService {

    private static final String ISSUER = "spendwise-auth-service";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public TokenService(JwtEncoder jwtEncoder,
                         JwtDecoder jwtDecoder,
                         RefreshTokenRepository refreshTokenRepository,
                         @Value("${spendwise.security.access-token-ttl-seconds}") long accessTokenTtlSeconds,
                         @Value("${spendwise.security.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Transactional
    public TokenPair issueTokenPair(Credential credential) {
        return mintPair(credential.getId(), credential.getEmail()).tokenPair();
    }

    @Transactional
    public TokenPair refresh(String refreshTokenJwt) {
        Jwt decoded;
        try {
            decoded = jwtDecoder.decode(refreshTokenJwt);
        } catch (RuntimeException e) {
            throw new InvalidRefreshTokenException("token is malformed, expired, or has an invalid signature");
        }

        if (!TYPE_REFRESH.equals(decoded.getClaimAsString(CLAIM_TYPE))) {
            throw new InvalidRefreshTokenException("token is not a refresh token");
        }

        UUID jti = UUID.fromString(decoded.getId());
        RefreshToken stored = refreshTokenRepository.findById(jti)
                .orElseThrow(() -> new InvalidRefreshTokenException("token is unknown to this server"));

        if (stored.isRevoked()) {
            throw new InvalidRefreshTokenException("token was already rotated out (possible reuse/replay)");
        }

        UUID credentialId = stored.getCredentialId();
        String email = decoded.getClaimAsString(CLAIM_EMAIL);

        MintedPair minted = mintPair(credentialId, email);
        stored.revoke(minted.refreshJti());
        refreshTokenRepository.save(stored);

        return minted.tokenPair();
    }

    private MintedPair mintPair(UUID credentialId, String email) {
        Instant now = Instant.now();
        String accessToken = encode(credentialId, email, now, accessTokenTtlSeconds, TYPE_ACCESS, null);

        UUID jti = UUID.randomUUID();
        String refreshToken = encode(credentialId, email, now, refreshTokenTtlSeconds, TYPE_REFRESH, jti);
        refreshTokenRepository.save(new RefreshToken(jti, credentialId, now, now.plusSeconds(refreshTokenTtlSeconds)));

        return new MintedPair(new TokenPair(accessToken, refreshToken, accessTokenTtlSeconds), jti);
    }

    private String encode(UUID credentialId, String email, Instant issuedAt, long ttlSeconds, String type, UUID jti) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(credentialId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(ttlSeconds));
        if (jti != null) {
            claims.id(jti.toString());
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    public record TokenPair(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
    }

    private record MintedPair(TokenPair tokenPair, UUID refreshJti) {
    }
}
