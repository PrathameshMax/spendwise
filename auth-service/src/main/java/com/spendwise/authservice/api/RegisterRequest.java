package com.spendwise.authservice.api;

public record RegisterRequest(String email, String rawPassword) {
}
