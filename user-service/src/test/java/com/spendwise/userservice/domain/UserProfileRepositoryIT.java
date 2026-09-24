package com.spendwise.userservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserProfileRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void enforcesUniqueEmailConstraintAtTheDatabaseLevel() {
        userProfileRepository.save(new UserProfile("jane@spendwise.dev", "Jane Doe", "INR"));

        assertThat(userProfileRepository.existsByEmail("jane@spendwise.dev")).isTrue();
    }

    @Test
    void updateProfileTouchesUpdatedAtButNotCreatedAt() {
        UserProfile saved = userProfileRepository.save(new UserProfile("mark@spendwise.dev", "Mark Lee", "USD"));
        var createdAt = saved.getCreatedAt();

        saved.updateProfile("Mark A. Lee", null);
        userProfileRepository.saveAndFlush(saved);

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getFullName()).isEqualTo("Mark A. Lee");
        assertThat(saved.getPreferredCurrency()).isEqualTo("USD");
    }
}
