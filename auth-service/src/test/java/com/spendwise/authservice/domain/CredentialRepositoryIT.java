package com.spendwise.authservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CredentialRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    void savesAndFindsCredentialByEmail() {
        Credential credential = new Credential("chaos.lab@spendwise.dev", "hashed-password");
        credentialRepository.save(credential);

        Optional<Credential> found = credentialRepository.findByEmail("chaos.lab@spendwise.dev");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void existsByEmailReflectsDuplicateConstraintUsage() {
        credentialRepository.save(new Credential("duplicate@spendwise.dev", "hashed-password"));

        assertThat(credentialRepository.existsByEmail("duplicate@spendwise.dev")).isTrue();
        assertThat(credentialRepository.existsByEmail("nobody@spendwise.dev")).isFalse();
    }
}
