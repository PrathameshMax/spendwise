package com.spendwise.transactionservice.api;

import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.common.security.KeycloakGrantedAuthoritiesConverter;
import com.spendwise.transactionservice.config.GlobalExceptionHandler;
import com.spendwise.transactionservice.domain.TransactionStatus;
import com.spendwise.transactionservice.domain.TransactionType;
import com.spendwise.transactionservice.service.StatementMerchantEnricher;
import com.spendwise.transactionservice.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "We test security like a feature": negative tests for 401, 403 and IDOR against
 * the real filter chain (spendwise-common's resource-server auto-configuration,
 * imported into the MVC slice) and the real Keycloak authority mapping.
 * {@code @ContextConfiguration} keeps the application class (and its
 * {@code @EnableFeignClients}) out of the slice.
 */
@WebMvcTest(controllers = TransactionController.class)
@ContextConfiguration(classes = {TransactionController.class, GlobalExceptionHandler.class})
class TransactionControllerSecurityTest {

    private static final UUID ALICE = UUID.fromString("7f3c9a2e-0d3b-4d0e-9c41-2b8f6c1a0001");
    private static final UUID BOB = UUID.fromString("7f3c9a2e-0d3b-4d0e-9c41-2b8f6c1a0002");
    private static final UUID BOBS_TRANSACTION = UUID.fromString("0b1e2c3d-4e5f-4a6b-8c7d-000000000042");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private StatementMerchantEnricher statementMerchantEnricher;

    @Test
    void requestWithoutTokenIs401WithProblemBodyAndWwwAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", BOBS_TRANSACTION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void validTokenWithoutTheRequiredScopeIs403() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", BOBS_TRANSACTION)
                        .with(customer(ALICE, "budgets:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void validTokenWithoutTheCustomerRoleIs403() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", BOBS_TRANSACTION)
                        .with(token(ALICE, List.of("SERVICE"), "transactions:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @Test
    void readingAnotherUsersTransactionIs404NotTheTransaction() throws Exception {
        given(transactionService.getOwned(ALICE, BOBS_TRANSACTION))
                .willThrow(new ResourceNotFoundException("Transaction", BOBS_TRANSACTION));

        mockMvc.perform(get("/api/v1/transactions/{id}", BOBS_TRANSACTION)
                        .with(customer(ALICE, "transactions:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        verify(transactionService).getOwned(ALICE, BOBS_TRANSACTION);
    }

    @Test
    void ownerReadsOwnTransaction() throws Exception {
        given(transactionService.getOwned(BOB, BOBS_TRANSACTION)).willReturn(sampleResponse(BOB));

        mockMvc.perform(get("/api/v1/transactions/{id}", BOBS_TRANSACTION)
                        .with(customer(BOB, "transactions:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(BOB.toString()));
    }

    @Test
    void userIdSmuggledIntoTheBodyIsIgnoredOwnerComesFromTheToken() throws Exception {
        given(transactionService.create(eq(ALICE), any(CreateTransactionRequest.class)))
                .willReturn(sampleResponse(ALICE));
        String body = """
                {
                  "userId": "%s",
                  "categoryId": "%s",
                  "amount": 250.00,
                  "type": "EXPENSE",
                  "description": "groceries",
                  "transactionDate": "%s",
                  "currency": "INR"
                }
                """.formatted(BOB, UUID.randomUUID(), LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/transactions")
                        .with(customer(ALICE, "transactions:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(transactionService).create(eq(ALICE), any(CreateTransactionRequest.class));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(UUID subject, String scope) {
        return token(subject, List.of("CUSTOMER"), scope);
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(UUID subject,
                                                                                    List<String> realmRoles,
                                                                                    String scope) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject(subject.toString())
                        .claim("email", "test@spendwise.dev")
                        .claim("scope", scope)
                        .claim("realm_access", Map.of("roles", realmRoles)))
                .authorities(new KeycloakGrantedAuthoritiesConverter());
    }

    private static TransactionResponse sampleResponse(UUID owner) {
        return new TransactionResponse(BOBS_TRANSACTION, owner, UUID.randomUUID(), "Groceries",
                new BigDecimal("250.00"), TransactionType.EXPENSE, "groceries", LocalDate.now().minusDays(1),
                Instant.now(), "INR", null, TransactionStatus.POSTED, null, null, null);
    }
}
