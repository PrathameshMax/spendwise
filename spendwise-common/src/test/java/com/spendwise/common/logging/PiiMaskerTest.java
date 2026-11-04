package com.spendwise.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void masksEmailsAccountNumbersAndTokens() {
        String line = "user alice@spendwise.dev paid from 4111111111111111 with "
                + "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln";

        String masked = PiiMasker.mask(line);

        assertThat(masked)
                .contains("a***@spendwise.dev")
                .contains("****1111")
                .contains("Bearer [REDACTED]")
                .doesNotContain("alice@")
                .doesNotContain("4111111111111111")
                .doesNotContain("eyJ");
    }

    @Test
    void leavesUuidsReadable() {
        String id = "7f3c9a2e-0d3b-4d0e-9c41-000000000001";

        assertThat(PiiMasker.mask("transaction " + id)).contains(id);
    }

    @Test
    void masksSingleEmailValue() {
        assertThat(PiiMasker.maskEmail("bob@spendwise.dev")).isEqualTo("b***@spendwise.dev");
        assertThat(PiiMasker.maskEmail("not-an-email")).isEqualTo("***");
    }
}
