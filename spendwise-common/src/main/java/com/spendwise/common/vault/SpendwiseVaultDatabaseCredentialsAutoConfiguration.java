package com.spendwise.common.vault;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.vault.core.lease.SecretLeaseContainer;

import javax.sql.DataSource;

/**
 * Registers {@link VaultDatabaseCredentialRotator} in every service that takes its
 * Postgres login from Vault's database secrets engine
 * ({@code spring.cloud.vault.database.enabled=true}: user-, transaction- and
 * budget-service). Watches the same path Spring Cloud Vault reads:
 * {@code <backend>/creds/<role>}, or {@code <backend>/static-creds/<role>} for a
 * static role.
 */
@AutoConfiguration
@ConditionalOnClass({SecretLeaseContainer.class, HikariDataSource.class})
@ConditionalOnProperty(prefix = "spring.cloud.vault.database", name = {"enabled", "role"})
public class SpendwiseVaultDatabaseCredentialsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultDatabaseCredentialRotator vaultDatabaseCredentialRotator(
            ObjectProvider<SecretLeaseContainer> leaseContainer,
            ObjectProvider<DataSource> dataSource,
            Environment environment) {
        return new VaultDatabaseCredentialRotator(leaseContainer, dataSource, credentialsPath(environment));
    }

    static String credentialsPath(Environment environment) {
        String backend = environment.getProperty("spring.cloud.vault.database.backend", "database");
        String role = environment.getRequiredProperty("spring.cloud.vault.database.role");
        boolean staticRole = environment.getProperty("spring.cloud.vault.database.static-role", Boolean.class, false);
        return backend + (staticRole ? "/static-creds/" : "/creds/") + role;
    }
}
