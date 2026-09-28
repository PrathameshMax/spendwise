package com.spendwise.authservice.api;

public record LoginRequest(String email, String rawPassword) {
}
