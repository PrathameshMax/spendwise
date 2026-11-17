package com.spendwise.common.vault;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultDatabaseCredentialRotatorTest {

    private static final String PATH = "database/creds/user-service";

    private SecretLeaseContainer leaseContainer;
    private HikariDataSource hikari;
    private VaultDatabaseCredentialRotator rotator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        leaseContainer = mock(SecretLeaseContainer.class);
        hikari = new HikariDataSource();
        hikari.setJdbcUrl("jdbc:postgresql://localhost:5432/user_db");
        hikari.setUsername("v-token-us-user-ser-old");
        hikari.setPassword("old-password");

        ObjectProvider<SecretLeaseContainer> containerProvider = mock(ObjectProvider.class);
        when(containerProvider.getIfAvailable()).thenReturn(leaseContainer);
        ObjectProvider<DataSource> dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(hikari);

        rotator = new VaultDatabaseCredentialRotator(containerProvider, dataSourceProvider, PATH);
        rotator.afterSingletonsInstantiated();
    }

    private static Lease lease(String id) {
        return Lease.of(id, Duration.ofHours(1), true);
    }

    @Test
    void registersWithTheLeaseContainer() {
        verify(leaseContainer).addLeaseListener(rotator);
        verify(leaseContainer).addErrorListener(rotator);
    }

    @Test
    void endedRenewableLeaseRequestsRotatingCredentialsOnce() {
        RequestedSecret renewable = RequestedSecret.renewable(PATH);

        rotator.onLeaseEvent(new SecretLeaseExpiredEvent(renewable, lease("database/creds/user-service/a")));
        rotator.onLeaseEvent(new SecretLeaseExpiredEvent(renewable, lease("database/creds/user-service/a")));

        verify(leaseContainer, times(1)).requestRotatingSecret(PATH);
    }

    @Test
    void rotatedCredentialsAreAppliedToThePool() {
        RequestedSecret rotating = RequestedSecret.rotating(PATH);

        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(rotating, lease("database/creds/user-service/b"),
                Map.of("username", "v-token-us-user-ser-new", "password", "new-password")));

        assertThat(hikari.getUsername()).isEqualTo("v-token-us-user-ser-new");
        assertThat(hikari.getPassword()).isEqualTo("new-password");
    }

    @Test
    void initialRenewableSecretAndOtherPathsAreIgnored() {
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.renewable(PATH), lease("x"),
                Map.of("username", "startup-user", "password", "startup-password")));
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.rotating("secret/data/user-service"),
                lease("y"), Map.of("username", "other", "password", "other")));
        rotator.onLeaseEvent(new SecretLeaseExpiredEvent(RequestedSecret.renewable("database/creds/budget-service"),
                lease("z")));

        assertThat(hikari.getUsername()).isEqualTo("v-token-us-user-ser-old");
        verify(leaseContainer, never()).requestRotatingSecret(anyString());
    }

    @Test
    void responseWithoutCredentialsKeepsTheCurrentLogin() {
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.rotating(PATH), lease("c"),
                Map.of("username", "v-token-us-user-ser-new")));

        assertThat(hikari.getUsername()).isEqualTo("v-token-us-user-ser-old");
        assertThat(hikari.getPassword()).isEqualTo("old-password");
    }

    @Test
    void pathFollowsSpringCloudVaultDatabaseProperties() {
        MockEnvironment dynamic = new MockEnvironment()
                .withProperty("spring.cloud.vault.database.role", "user-service");
        MockEnvironment staticRole = new MockEnvironment()
                .withProperty("spring.cloud.vault.database.backend", "postgres")
                .withProperty("spring.cloud.vault.database.role", "reporting")
                .withProperty("spring.cloud.vault.database.static-role", "true");

        assertThat(SpendwiseVaultDatabaseCredentialsAutoConfiguration.credentialsPath(dynamic))
                .isEqualTo("database/creds/user-service");
        assertThat(SpendwiseVaultDatabaseCredentialsAutoConfiguration.credentialsPath(staticRole))
                .isEqualTo("postgres/static-creds/reporting");
    }
}
