package com.spendwise.common.exception;

/**
 * Recognises Spring Security exceptions by class name, so the shared exception
 * handlers can hand them back to the security filter chain without a compile-time
 * dependency on Spring Security (services without it still load the handlers).
 */
final class SecurityExceptions {

    private static final String ACCESS_DENIED = "org.springframework.security.access.AccessDeniedException";
    private static final String AUTHENTICATION = "org.springframework.security.core.AuthenticationException";

    private SecurityExceptions() {
    }

    static boolean isSecurityException(Throwable throwable) {
        for (Class<?> type = throwable.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (ACCESS_DENIED.equals(name) || AUTHENTICATION.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
