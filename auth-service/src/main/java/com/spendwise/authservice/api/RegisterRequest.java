package com.spendwise.authservice.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No create/update groups here (Milestone 8) — unlike user-service's merged
 * {@code UserRequest}, auth-service has no partial-update endpoint for
 * credentials, so every constraint applies unconditionally via the default
 * group and a plain {@code @Valid} on the controller is sufficient.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String rawPassword) {
}
