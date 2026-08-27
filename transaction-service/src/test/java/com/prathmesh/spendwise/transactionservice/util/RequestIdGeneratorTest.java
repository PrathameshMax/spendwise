package com.prathmesh.spendwise.transactionservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestIdGeneratorTest {

    @Test
    void generate_shouldReturnValidUuid() {

        String requestId = RequestIdGenerator.generate();

        assertNotNull(requestId);
        assertFalse(requestId.isBlank());

        assertDoesNotThrow(() ->
                java.util.UUID.fromString(requestId)
        );
    }

    @Test
    void generate_shouldReturnDifferentIds() {

        String first = RequestIdGenerator.generate();
        String second = RequestIdGenerator.generate();

        assertNotEquals(first, second);
    }
}
