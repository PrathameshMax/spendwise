package com.spendwise.userservice.api;

public record CreateUserRequest(String email, String fullName, String preferredCurrency) {
}
