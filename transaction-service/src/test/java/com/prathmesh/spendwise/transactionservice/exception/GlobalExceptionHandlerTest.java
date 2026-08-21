package com.prathmesh.spendwise.transactionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();


    @Test
    void transactionNotFound_shouldReturn404() {

        TransactionNotFoundException exception =
                new TransactionNotFoundException(
                        "Transaction not found with Id : 99"
                );

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/v1/transactions/99");

        ResponseEntity<?> response =
                handler.handleTransactionNotFound(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                404,
                ((java.util.Map<?, ?>) response.getBody())
                        .get("status")
        );

        assertEquals(
                "Transaction not found with Id : 99",
                ((java.util.Map<?, ?>) response.getBody())
                        .get("message")
        );
    }


    @Test
    void malformedRequest_shouldReturn400() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/v1/transactions");

        ResponseEntity<?> response =
                handler.handleMalformedRequest(
                        new org.springframework.http.converter
                                .HttpMessageNotReadableException(
                                "Malformed JSON"
                        ),
                        request
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                400,
                ((java.util.Map<?, ?>) response.getBody())
                        .get("status")
        );

        assertEquals(
                "Malformed request",
                ((java.util.Map<?, ?>) response.getBody())
                        .get("message")
        );
    }


    @Test
    void unexpectedException_shouldReturn500() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/v1/transactions");

        ResponseEntity<?> response =
                handler.handleUnexpectedException(
                        new RuntimeException(
                                "Database connection failed"
                        ),
                        request
                );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                500,
                ((java.util.Map<?, ?>) response.getBody())
                        .get("status")
        );

        assertEquals(
                "An unexpected error occurred",
                ((java.util.Map<?, ?>) response.getBody())
                        .get("message")
        );
    }
}
