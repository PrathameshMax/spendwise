package com.prathmesh.spendwise.userservice.exception;

import java.time.LocalDateTime;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;


@Schema(
        description = "Standard error response returned by the User Service"
)
public record ErrorResponse(

        @Schema(
                description = "Date and time when the error occurred",
                example = "2026-08-19T00:45:30"
        )
        LocalDateTime timestamp,

        @Schema(
                description = "HTTP status code",
                example = "400"
        )
        int status,

        @Schema(
                description = "Human-readable error message",
                example = "Validation failed"
        )
        String message,

        @Schema(
                description = "API path where the error occurred",
                example = "/api/v1/users"
        )
        String path,

        @Schema(
                description = "Validation errors grouped by field",
                example = "{\"phone\":\"Invalid mobile number\"}"
        )
        Map<String, String> errors
) {
}
