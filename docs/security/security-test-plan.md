# SpendWise security test round (Docker Compose)

Proves the controls listed in `security-walkthrough.md` on the running stack. Three parts:

| Part | Tool | Covers |
|---|---|---|
| A | Postman: `postman/SpendWise-Security.postman_collection.json` | 401/403, roles and scopes, ownership (IDOR), CORS, header stripping, size cap, security headers, rate limit, error hygiene, actuator, gRPC happy path, refresh rotation, logout, expiry |
| B | Browser: `http://localhost:5173` | CSRF on cookie sessions, silent token refresh, session revocation, sign-out |
| C | Git Bash | Audit log, PII masking, encryption at rest, gRPC without/with a token, Vault credential rotation, Kafka TLS + SASL + ACLs |

Everything below runs from Git Bash in `/d/spendwise` with the stack up (`docker compose up -d`).

---

## A. Postman

1. Postman → **Import** → `postman/SpendWise-Security.postman_collection.json`.
2. Run folders **0 to 9** in order (Collection Runner, or click through). Each request asserts its expected status and names the control in the test title.
3. Run folder **10** five or more minutes after folder 0 (it skips itself until the token is old enough).
4. If anything fails with `TOKEN_EXPIRED` before folder 10, re-run folder 0 (tokens live 5 minutes).

Folder 6 fires 60 parallel requests from one request's test script; the Postman console shows the status list.

---

## B. Browser (cookie session)

Rebuild the web app once so it has the CSRF card: `docker compose up -d --build web`.

| # | Do | Expect |
|---|---|---|
| B1 | Sign in at `http://localhost:5173` as alice. In **Security check: CSRF** click both buttons | *Without* → **403** `An expected CSRF token cannot be found`, PASS. *With* → **200**, PASS |
| B2 | Stay signed in, wait 6 minutes (access token lives 5), click **Call user-service** | **200**: the gateway used the refresh token behind the scenes; the browser never saw a token |
| B3 | Keycloak admin (`http://localhost:8180/admin`, admin/admin) → realm `spendwise` → **Sessions** → alice → **Sign out**. Wait 5 minutes, click **Call user-service** | **401** `Session expired; log in again`: the refresh is refused because the Keycloak session is gone |
| B4 | **Sign out** | Back on "You are signed out"; `logout` request shows `Origin: http://localhost:5173` |

After B3, a page reload may still say "You are signed in": `/bff/user` reads the gateway's own session, which outlives the Keycloak session until sign-out. Known gap, noted for the hardening list.

---

## C. Command-line checks

### C1. Audit log (after Postman folders 2-4)

```bash
docker logs spendwise-user-service 2>&1 | grep "security_event=" | tail -5
docker logs spendwise-transaction-service 2>&1 | grep "security_event=OWNERSHIP_CHECK_FAILED" | tail -3
docker logs spendwise-budget-service 2>&1 | grep "security_event=ADMIN_ACTION" | tail -3
docker logs spendwise-api-gateway 2>&1 | grep "jwt_rejected" | tail -5
```

Expect `ACCESS_DENIED status=403` (user-service), `OWNERSHIP_CHECK_FAILED resource=Transaction` (bob reading alice's transaction), `ADMIN_ACTION action=READ_BUDGET_SUMMARY`, and gateway lines `jwt_rejected reason=token_unverifiable / token_type_rejected / claims_invalid`. Every line carries a `correlationId` that matches the response's `X-Correlation-ID`.

### C2. PII masking in logs

```bash
for s in user-service transaction-service budget-service api-gateway; do
  printf "%-22s plain emails: %s   masked: %s   raw JWTs: %s\n" "$s" \
    "$(docker logs spendwise-$s 2>&1 | grep -cE '[A-Za-z0-9._-]{2,}@spendwise\.dev')" \
    "$(docker logs spendwise-$s 2>&1 | grep -c '\*\*\*@spendwise\.dev')" \
    "$(docker logs spendwise-$s 2>&1 | grep -c 'eyJ')"
done
```

Expect **0** plain emails and **0** raw JWTs everywhere. "masked" may be 0 too if nothing logged an email.

### C3. Field encryption at rest

```bash
docker exec spendwise-postgres psql -U spendwise -d user_db -c "SELECT email, left(full_name, 40) AS full_name_at_rest FROM user_profiles;"
```

Expect `full_name_at_rest` starting with `v1:` (AES-256-GCM ciphertext), while `GET /api/v1/users/me` returns the plain name.

### C4. gRPC: budget-service rejects calls without a valid token

```bash
cd /d/spendwise
ALICE_ID=7f3c9a2e-0d3b-4d0e-9c41-2b8f6c1a0001
BOB_ID=7f3c9a2e-0d3b-4d0e-9c41-2b8f6c1a0002
MONTH=$(date +%Y-%m)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/spendwise/protocol/openid-connect/token \
  -d grant_type=password -d client_id=spendwise-dev-cli -d scope=openid \
  --data-urlencode username=alice@spendwise.dev --data-urlencode 'password=Alice#Dev2026!' | jq -r .access_token)
grpc() {
  MSYS_NO_PATHCONV=1 docker run --rm --network spendwise_default \
    -v "$(pwd -W)/analytics-service/src/main/proto:/protos:ro" fullstorydev/grpcurl \
    -plaintext -import-path /protos -proto budget_summary.proto "$@" \
    budget-service:9090 spendwise.grpc.budget.BudgetSummaryService/GetSummary
}
grpc -d "{\"user_id\":\"$ALICE_ID\",\"period_month\":\"$MONTH\"}"
grpc -H "authorization: Bearer $TOKEN" -d "{\"user_id\":\"$ALICE_ID\",\"period_month\":\"$MONTH\"}"
grpc -H "authorization: Bearer $TOKEN" -d "{\"user_id\":\"$BOB_ID\",\"period_month\":\"$MONTH\"}"
```

| Call | Expect |
|---|---|
| no token | `Code: Unauthenticated` `A valid bearer access token is required` |
| alice's token, alice's id | a response (items may be empty) |
| alice's token, bob's id | `Code: PermissionDenied`, plus `OWNERSHIP_CHECK_FAILED resource=BudgetSummary` in budget-service's log |

If `docker run` says the network does not exist, `docker network ls` shows its real name.

### C5. Vault: database login replaced when its lease ends

Shortens user-service's database lease so the rotation happens within 5 minutes, then restores it.

```bash
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=dev-root-token spendwise-vault \
  vault write database/roles/user-service default_ttl=2m max_ttl=5m
docker compose restart user-service
docker logs -f spendwise-user-service 2>&1 | grep --line-buffered "vault.db.credentials"
```

Within about 5 minutes:

```
vault.db.credentials.watching path=database/creds/user-service
vault.db.credentials.expired path=database/creds/user-service lease=... - lease reached max TTL or was revoked; requesting new credentials
vault.db.credentials.rotated path=database/creds/user-service pool=... user=v-token-us-user-ser-... lease=... ttl=120s
```

Press Ctrl+C, run Postman **alice · GET /users/me** (expect 200), and list the logins Vault created:

```bash
docker exec spendwise-postgres psql -U spendwise -d postgres -c "SELECT rolname, rolvaliduntil FROM pg_roles WHERE rolname LIKE 'v-token-us-user%' ORDER BY rolvaliduntil;"
```

Restore the normal 1h/24h lease (vault-init keeps the live tokens):

```bash
docker compose up -d vault-init
docker logs spendwise-vault-init --tail 6
```

### C6. Kafka: TLS + SASL + topic ACLs

Switch the brokers and clients to the secure overlay:

```bash
bash infra/kafka/secure/generate-certs.sh
docker compose -f docker-compose.yml -f docker-compose.kafka-secure.yml up -d
docker logs spendwise-kafka-acl-init --tail 40
```

Expect the ACL listing at the end of `kafka-acl-init`. Then run Postman folders **0, 1 and 8**: the transaction is published and consumed over SASL_SSL, and the dashboard reflects it. Look for authentication or authorization errors in the clients:

```bash
for s in transaction-service budget-service notification-service analytics-service; do
  echo "== $s"; docker compose logs --tail 300 $s 2>&1 | grep -iE "SaslAuthentication|TopicAuthorization|GroupAuthorization|SSLHandshake" | tail -3
done
```

Expect no lines. Now the negative tests, run as a throw-away Kafka client on the compose network:

```bash
kafka_client() {   # usage: kafka_client <user> <password> <protocol>
  MSYS_NO_PATHCONV=1 docker run --rm -i --network spendwise_default \
    -v "$(pwd -W)/infra/kafka/secure/secrets:/etc/kafka/secrets:ro" apache/kafka:3.9.0 bash -c "
cat > /tmp/client.properties <<EOF
security.protocol=$3
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$1\" password=\"$2\";
ssl.truststore.type=PEM
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.pem
EOF
echo forged-event | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka-1:19092 \
  --producer.config /tmp/client.properties --producer-property max.block.ms=10000 --topic transaction-events"
}
kafka_client svc_notification dev-kafka-notification-secret SASL_SSL
kafka_client svc_transaction wrong-password SASL_SSL
kafka_client svc_transaction dev-kafka-transaction-secret PLAINTEXT
```

| Client | Expect |
|---|---|
| svc_notification (valid login, no write ACL) | `TopicAuthorizationException: Not authorized to access topics: [transaction-events]` |
| svc_transaction, wrong password | `SaslAuthenticationException: Authentication failed` |
| PLAINTEXT, no TLS | `TimeoutException` after 10 s (the broker only speaks SASL_SSL) |

Back to the normal (PLAINTEXT) setup when done:

```bash
docker compose -f docker-compose.yml up -d
docker rm spendwise-kafka-acl-init
```

---

## Results

| # | Control | Result |
|---|---|---|
| A0-A1 | Tokens issued; seed data | |
| A2 | 401 with reason codes | |
| A3 | Roles and scopes (403) | |
| A4 | Ownership / IDOR (404) | |
| A5 | CORS, header stripping, size cap, security headers | |
| A6 | Rate limit per user (429 + Retry-After) | |
| A7 | Error hygiene, actuator lock-down | |
| A8 | Service-to-service (gRPC happy path) | |
| A9 | Refresh rotation, reuse detection, logout | |
| A10 | Expired token | |
| B1-B4 | CSRF, silent refresh, revocation, sign-out | |
| C1 | Audit events with correlation ids | |
| C2 | No plain emails or JWTs in logs | |
| C3 | Encrypted at rest | |
| C4 | gRPC Unauthenticated / PermissionDenied | |
| C5 | Vault login rotation | |
| C6 | Kafka TLS + SASL + ACLs | |
