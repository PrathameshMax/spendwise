package com.spendwise.authservice.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * No {@code @Size} on {@code rawPassword} here (Milestone 8) — unlike
 * {@code RegisterRequest}, a login attempt must be allowed to fail with the
 * generic {@code InvalidCredentialsException} rather than a validation error,
 * so this only rejects structurally empty input, never a "too short to be a
 * real password" guess that would leak information about password policy to
 * an attacker probing the login endpoint.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String rawPassword) {
}
