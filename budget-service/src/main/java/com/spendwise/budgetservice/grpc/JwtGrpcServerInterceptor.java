package com.spendwise.budgetservice.grpc;

import com.spendwise.common.security.KeycloakJwtAuthenticationConverter;
import com.spendwise.common.security.SecurityAuditLogger;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Hop ⑤ for the gRPC port: the HTTP filter chain does not cover gRPC, so the same
 * zero-trust rule is enforced here. Every call must carry
 * {@code authorization: Bearer <jwt>} metadata; the token is validated with the
 * service's {@link JwtDecoder} (same JWKS, issuer, audience and type checks as
 * HTTP) and converted with the same Keycloak authority mapping. The resulting
 * {@link Authentication} is bound to the gRPC {@link Context} for the call.
 * Missing or invalid token → {@code UNAUTHENTICATED}.
 */
public class JwtGrpcServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<Authentication> AUTHENTICATION = Context.key("spendwise-authentication");
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter authenticationConverter = new KeycloakJwtAuthenticationConverter();

    public JwtGrpcServerInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * The authenticated caller of the current gRPC call, or {@code null} outside one.
     */
    public static Authentication currentAuthentication() {
        return AUTHENTICATION.get();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        String header = headers.get(AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return reject(call, method, "missing bearer token");
        }
        Authentication authentication;
        try {
            authentication = authenticationConverter.convert(
                    jwtDecoder.decode(header.substring(BEARER_PREFIX.length()).trim()));
        } catch (JwtException e) {
            return reject(call, method, e.getMessage());
        }
        Context context = Context.current().withValue(AUTHENTICATION, authentication);
        return Contexts.interceptCall(context, call, headers, next);
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call,
                                                                   String method,
                                                                   String reason) {
        SecurityAuditLogger.authenticationFailed("GRPC", method, reason, null);
        call.close(Status.UNAUTHENTICATED.withDescription("A valid bearer access token is required"), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
