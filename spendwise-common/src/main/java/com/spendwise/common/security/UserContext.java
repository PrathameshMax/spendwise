package com.spendwise.common.security;

import java.util.UUID;

/**
 * The caller identity the API Gateway resolves from a validated JWT and forwards
 * downstream as a signed header (Milestone 6). Carries only what every business
 * service needs to trust who is calling — no role/claim list, since {@code Credential}
 * has no roles concept yet; role-based/claim-based authorization (an interview
 * discussion topic for this milestone) is added once that concept exists in the
 * domain, rather than invented here ahead of it.
 */
public record UserContext(UUID userId, String email) {
}
