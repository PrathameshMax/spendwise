package com.spendwise.common.vault;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRenewedEvent;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the Hikari pool on valid Vault database credentials for the whole life of
 * the service.
 *
 * <p>Spring Cloud Vault fetches {@code database/creds/<role>} once at startup as a
 * <em>renewable</em> lease and renews it until Vault refuses: the role's
 * {@code max_ttl} is reached, or the lease was revoked (its token was revoked, or
 * Vault restarted). From then on Postgres rejects every new connection with
 * {@code 28P01 password authentication failed}, and nothing in Spring swaps the
 * login. This listener closes that gap:
 *
 * <ol>
 *   <li>renewable lease ends ({@link SecretLeaseExpiredEvent}, mode RENEW) &rarr;
 *       request the same path as a <em>rotating</em> secret, once;</li>
 *   <li>rotating secret issued or rotated ({@link SecretLeaseCreatedEvent}, mode
 *       ROTATE) &rarr; set the new username/password on the pool and soft-evict, so
 *       idle connections close now and busy ones close when returned. Hikari
 *       5.x reads the credentials from its config on every new connection, for
 *       JDBC-URL pools too;</li>
 *   <li>from then on the lease container rotates by itself before each lease
 *       ends, and every rotation lands in step 2.</li>
 * </ol>
 *
 * <p>Passwords are never logged; the Vault-generated username and the lease id
 * are, so a rotation can be matched to the Postgres role in the database log.
 */
public class VaultDatabaseCredentialRotator implements LeaseListener, LeaseErrorListener,
        SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(VaultDatabaseCredentialRotator.class);

    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private final ObjectProvider<SecretLeaseContainer> leaseContainerProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final String credentialsPath;
    private final AtomicBoolean rotationRequested = new AtomicBoolean();

    private volatile SecretLeaseContainer leaseContainer;

    public VaultDatabaseCredentialRotator(ObjectProvider<SecretLeaseContainer> leaseContainerProvider,
                                          ObjectProvider<DataSource> dataSourceProvider,
                                          String credentialsPath) {
        this.leaseContainerProvider = leaseContainerProvider;
        this.dataSourceProvider = dataSourceProvider;
        this.credentialsPath = credentialsPath;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    @Override
    public void afterSingletonsInstantiated() {
        SecretLeaseContainer container = leaseContainerProvider.getIfAvailable();
        if (container == null) {
            log.warn("vault.db.credentials.unwatched path={} reason=no SecretLeaseContainer; "
                    + "database credentials will not be replaced when their Vault lease ends", credentialsPath);
            return;
        }
        container.addLeaseListener(this);
        container.addErrorListener(this);
        this.leaseContainer = container;
        log.info("vault.db.credentials.watching path={}", credentialsPath);
    }

    @Override
    public void destroy() {
        SecretLeaseContainer container = this.leaseContainer;
        if (container != null) {
            container.removeLeaseListener(this);
            container.removeLeaseErrorListener(this);
        }
    }

    @Override
    public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
        RequestedSecret secret = leaseEvent.getSource();
        if (!credentialsPath.equals(secret.getPath())) {
            return;
        }
        if (leaseEvent instanceof SecretLeaseCreatedEvent created) {
            if (secret.getMode() == RequestedSecret.Mode.ROTATE) {
                applyCredentials(created);
            }
            return;
        }
        if (leaseEvent instanceof AfterSecretLeaseRenewedEvent) {
            log.debug("vault.db.credentials.renewed path={} lease={} ttl={}s", credentialsPath,
                    leaseId(leaseEvent.getLease()), ttlSeconds(leaseEvent.getLease()));
            return;
        }
        if (leaseEvent instanceof SecretLeaseExpiredEvent) {
            if (secret.getMode() == RequestedSecret.Mode.RENEW) {
                requestRotatingCredentials(leaseEvent.getLease());
            } else {
                log.error("vault.db.credentials.rotation_failed path={} lease={} - Vault did not issue replacement "
                                + "credentials; new database connections fail until Vault is reachable and the "
                                + "service is restarted", credentialsPath, leaseId(leaseEvent.getLease()));
            }
        }
    }

    @Override
    public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
        if (!credentialsPath.equals(leaseEvent.getSource().getPath())) {
            return;
        }
        log.error("vault.db.credentials.lease_error path={} mode={} lease={} error={}", credentialsPath,
                leaseEvent.getSource().getMode(), leaseId(leaseEvent.getLease()), exception.getMessage());
    }

    private void requestRotatingCredentials(Lease endedLease) {
        SecretLeaseContainer container = this.leaseContainer;
        if (container == null) {
            return;
        }
        if (!rotationRequested.compareAndSet(false, true)) {
            log.warn("vault.db.credentials.expired path={} lease={} - rotation already requested",
                    credentialsPath, leaseId(endedLease));
            return;
        }
        log.warn("vault.db.credentials.expired path={} lease={} - lease reached max TTL or was revoked; "
                + "requesting new credentials", credentialsPath, leaseId(endedLease));
        container.requestRotatingSecret(credentialsPath);
    }

    private synchronized void applyCredentials(SecretLeaseCreatedEvent event) {
        Map<String, Object> secrets = event.getSecrets();
        Object username = secrets.get(USERNAME_KEY);
        Object password = secrets.get(PASSWORD_KEY);
        if (!(username instanceof String newUsername) || !(password instanceof String newPassword)
                || newUsername.isBlank() || newPassword.isBlank()) {
            log.error("vault.db.credentials.rotation_failed path={} lease={} - response has no username/password "
                    + "(keys {}); pool keeps its current credentials", credentialsPath,
                    leaseId(event.getLease()), secrets.keySet());
            return;
        }

        HikariDataSource hikari = hikariDataSource();
        if (hikari == null) {
            log.error("vault.db.credentials.rotation_failed path={} lease={} - no HikariDataSource to update",
                    credentialsPath, leaseId(event.getLease()));
            return;
        }

        HikariConfigMXBean config = hikari.getHikariConfigMXBean();
        config.setUsername(newUsername);
        config.setPassword(newPassword);

        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool != null) {
            pool.softEvictConnections();
        }

        log.info("vault.db.credentials.rotated path={} pool={} user={} lease={} ttl={}s - idle connections "
                        + "evicted, busy connections retire when returned", credentialsPath, hikari.getPoolName(),
                newUsername, leaseId(event.getLease()), ttlSeconds(event.getLease()));
    }

    private HikariDataSource hikariDataSource() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return null;
        }
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            return dataSource.isWrapperFor(HikariDataSource.class) ? dataSource.unwrap(HikariDataSource.class) : null;
        } catch (SQLException e) {
            log.error("vault.db.credentials.unwrap_failed path={} dataSource={} error={}", credentialsPath,
                    dataSource.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private static String leaseId(Lease lease) {
        return lease != null && lease.hasLeaseId() ? lease.getLeaseId() : "none";
    }

    private static long ttlSeconds(Lease lease) {
        return lease != null && lease.getLeaseDuration() != null ? lease.getLeaseDuration().getSeconds() : 0L;
    }
}
