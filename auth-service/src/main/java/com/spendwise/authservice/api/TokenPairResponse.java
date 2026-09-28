package com.spendwise.authservice.api;

public record TokenPairResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {

    public static TokenPairResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenPairResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
