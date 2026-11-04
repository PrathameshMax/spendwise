# SpendWise application security — code map

Follow one request from the browser to the database; every hop has a control,
then four cross-cutting controls apply everywhere.

## ① Identity — who are you?
| Control | Where |
|---|---|
| Keycloak realm: clients, roles (CUSTOMER/ADMIN/SERVICE), scopes, audience mapper, brute-force lockout, password policy, TOTP policy, 5-min access tokens, refresh-token rotation (`revokeRefreshToken`, `refreshTokenMaxReuse: 0`), login/logout events | `infra/keycloak/spendwise-realm.json` |
| Keycloak container (issuer pinned to the browser URL) | `docker-compose.yml` → `keycloak` |
| Authorization Code + PKCE (S256), tokens kept in the server-side session | `api-gateway/.../config/KeycloakClientConfig.java` |
| Browser holds only an HttpOnly/Secure/SameSite session cookie (Redis-backed) | `config-repo/api-gateway.yml` → `server.reactive.session.cookie` |
| SPA session check | `api-gateway/.../api/BffSessionController.java` |

## ② Gateway — one front door
| Control | Where |
|---|---|
| Filter chain: BFF login, bearer validation, CORS, CSRF, HSTS/CSP/frame/referrer headers, logout | `api-gateway/.../config/SecurityConfig.java` |
| Signature + exp/iss/aud/typ/sub/email checks, reason-coded rejections | `api-gateway/.../security/AccessTokenClaimsValidator.java`, `JwtRejectionReason.java`, `GatewayJwtDecoder.java` |
| JWKS cache (rotation, outage tolerance, kid-flood protection) | `api-gateway/.../security/JwksKeyCache.java` |
| RFC 7807 401 / 503 with correlation id and metrics | `api-gateway/.../security/AuthenticationProblemHandler.java` |
| Strip spoofed identity headers (`X-User-Id`, ...) | `api-gateway/.../filter/IdentityHeaderStrippingWebFilter.java` |
| Rate limit per user (`sub`) / per IP | `api-gateway/.../config/RateLimitConfig.java` |
| Correlation id | `api-gateway/.../filter/CorrelationIdGlobalFilter.java` |
| Token relay, request size cap, Cookie never forwarded | `api-gateway/.../config/RouteConfig.java` |

## ③ Service — validate again, then authorize
| Control | Where |
|---|---|
| Every service is a resource server (zero trust), stateless chain, URL rules | `spendwise-common/.../security/SpendwiseResourceServerAutoConfiguration.java` (servlet), `SpendwiseReactiveResourceServerAutoConfiguration.java` (WebFlux) |
| exp/iss/aud/typ validators | `spendwise-common/.../security/SpendwiseJwtValidators.java` |
| `realm_access.roles` → `ROLE_*`, `scope` → `SCOPE_*` | `spendwise-common/.../security/KeycloakGrantedAuthoritiesConverter.java`, `KeycloakJwtAuthenticationConverter.java` |
| Method-level `@PreAuthorize` (roles + scopes) | `TransactionController`, `CategoryController`, `InsightsController`, `BudgetController`, `UserController`, `AnalyticsController` |
| 401 vs 403 as RFC 7807 + `WWW-Authenticate` | `spendwise-common/.../security/ServletSecurityProblemHandler.java`, `ReactiveSecurityProblemHandler.java` |
| 403s from `@PreAuthorize` are not swallowed as 500 | `spendwise-common/.../exception/SecurityExceptions.java` + both global exception handlers |

## ④ Data ownership — is this record yours?
| Control | Where |
|---|---|
| Caller only from the token's `sub` | `spendwise-common/.../security/CurrentUser.java`, `ReactiveCurrentUser.java` |
| No `userId` in request bodies or paths | `CreateTransactionRequest`, `CreateBudgetRequest`, `/users/me`, `/budgets/summary`, `/analytics/dashboard` |
| `findByIdAndUserId` — not yours = not found (IDOR/BOLA) | `TransactionRepository`, `TransactionService#getOwned` |
| Ownership over gRPC | `budget-service/.../grpc/BudgetSummaryGrpcService.java` |

## ⑤ Service-to-service — no back doors
| Control | Where |
|---|---|
| User token relayed on Feign calls; client credentials when no user | `spendwise-common/.../security/BearerTokenRelayRequestInterceptor.java`, `SpendwiseServiceTokenAutoConfiguration.java` |
| SecurityContext carried onto async threads | `transaction-service/.../client/AsyncUserServiceLookup.java` |
| gRPC: token in metadata, validated server-side | `analytics-service/.../grpc/BudgetSummaryGrpcClient.java`, `budget-service/.../grpc/JwtGrpcServerInterceptor.java`, `config/GrpcSecurityConfig.java` |
| Kafka TLS + SASL + topic ACLs | `docker-compose.kafka-secure.yml`, `infra/kafka/secure/*`, `config-repo/application-kafka-secure.yml` |
| ClusterIP services, NetworkPolicies, Istio STRICT mTLS + AuthorizationPolicies | `infra/k8s/security/*.yaml` |

## Cross-cutting
| Control | Where |
|---|---|
| Secrets: Vault KV, dynamic Postgres credentials, per-service policies | `infra/vault/init.sh`, each service's `application.yml` (`spring.cloud.vault`) |
| Vault Kubernetes auth (service-account login) | `infra/k8s/security/30-vault-kubernetes-auth.sh` |
| Field-level AES-256-GCM | `spendwise-common/.../crypto/AesGcmFieldEncryptor.java`, `EncryptedStringConverter.java`, `UserProfile#fullName` |
| PII masked in logs and responses | `spendwise-common/.../logging/PiiMasker.java`, `PiiMaskingEncoder.java`, `logback-spring.xml`, `MaskedEmailSerializer.java` → `UserSummaryResponse` |
| Secure coding: no stack traces, actuator/Swagger locked in prod | `config-repo/application.yml` (`server.error`), `config-repo/application-prod.yml` |
| Security audit events (401, 403, ownership, admin actions) | `spendwise-common/.../security/SecurityAuditLogger.java` |

## Token lifecycle and tests
| Control | Where |
|---|---|
| 5-min access tokens, refresh rotation + reuse revocation, SSO idle/max | realm JSON |
| Logout ends the Keycloak session (RP-initiated) and the gateway session | `SecurityConfig` → `OidcClientInitiatedServerLogoutSuccessHandler` |
| Expired/revoked refresh → 401, SPA re-logs in | `RouteConfig#tokenRelayWithReauthentication` |
| Negative tests: 401, 403, IDOR | `TransactionControllerSecurityTest`, `TransactionRepositoryIT#ownerScopedLookup...`, `GatewayJwtValidationTest`, `JwksKeyCacheTest` |
