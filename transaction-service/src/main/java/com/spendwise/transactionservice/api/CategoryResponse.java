package com.spendwise.transactionservice.api;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, boolean systemDefault) {
}
