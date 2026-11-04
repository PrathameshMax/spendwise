package com.spendwise.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security events, written to a dedicated {@code SECURITY_AUDIT} logger as
 * key=value pairs so Splunk can index and alert on them (e.g. a spike in
 * {@code AUTHENTICATION_FAILED}). Never logs tokens, passwords or full PII:
 * only the subject id, method, path, a reason and the correlation id.
 * Logins, logouts and refresh-token reuse are Keycloak events (realm event
 * listener), shipped alongside these.
 */
public final class SecurityAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private SecurityAuditLogger() {
    }

    public static void authenticationFailed(String method, String path, String reason, String correlationId) {
        AUDIT.warn("security_event=AUTHENTICATION_FAILED status=401 method={} path={} reason=\"{}\" correlationId={}",
                method, path, sanitize(reason), correlationId);
    }

    public static void accessDenied(String method, String path, String subject, String correlationId) {
        AUDIT.warn("security_event=ACCESS_DENIED status=403 method={} path={} subject={} correlationId={}",
                method, path, subject, correlationId);
    }

    public static void ownershipViolation(String resource, Object resourceId, String subject) {
        AUDIT.warn("security_event=OWNERSHIP_CHECK_FAILED resource={} resourceId={} subject={}",
                resource, resourceId, subject);
    }

    public static void adminAction(String action, String subject, Object target) {
        AUDIT.info("security_event=ADMIN_ACTION action={} subject={} target={}", action, subject, target);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('"', '\'').replace('\n', ' ').replace('\r', ' ');
    }
}
