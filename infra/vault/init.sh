#!/bin/sh
# Seeds the dev Vault (docker compose service "vault-init", runs on every `up`).
#
#   secret/<service>            KV v2 secrets each service reads at startup
#   database/creds/<service>    short-lived Postgres users (1h TTL, 24h max)
#   policy <service>            least privilege: only its own paths
#   token dev-token-<service>   dev stand-in for Kubernetes auth (one per service)
#
# Nothing here is a production procedure: dev mode keeps everything in memory and
# the token ids are fixed so docker-compose.yml can hand them to the services.
set -eu

echo "Waiting for Vault at ${VAULT_ADDR} ..."
until vault status >/dev/null 2>&1; do
  sleep 1
done

# --- KV v2 secrets (dev mode already mounts kv-v2 at secret/) -----------------
vault kv put secret/user-service \
  spendwise.security.field-encryption.key="${USER_SERVICE_FIELD_ENCRYPTION_KEY}"

vault kv put secret/transaction-service \
  spring.security.oauth2.client.registration.spendwise-service.client-secret="${TRANSACTION_SERVICE_CLIENT_SECRET}"

vault kv put secret/budget-service \
  spring.security.oauth2.client.registration.spendwise-service.client-secret="${BUDGET_SERVICE_CLIENT_SECRET}"

vault kv put secret/analytics-service \
  spendwise.security.vault-source="vault"

# --- Database secrets engine: dynamic, short-lived Postgres credentials ---------
vault secrets list -format=json | grep -q '"database/"' || vault secrets enable -path=database database

vault write database/config/spendwise-postgres \
  plugin_name=postgresql-database-plugin \
  allowed_roles="user-service,transaction-service,budget-service" \
  connection_url="postgresql://{{username}}:{{password}}@postgres:5432/postgres?sslmode=disable" \
  username="${POSTGRES_ADMIN_USER}" \
  password="${POSTGRES_ADMIN_PASSWORD}"

# Each generated login is a member of the schema-owner role "spendwise", so it can
# use the existing tables; Flyway runs "SET ROLE spendwise" so anything it creates
# is owned by that role, not by the short-lived login, which is dropped at expiry.
for pair in user-service:user_db transaction-service:transaction_db budget-service:budget_db; do
  role="${pair%%:*}"
  db="${pair##*:}"
  vault write "database/roles/${role}" \
    db_name=spendwise-postgres \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}' IN ROLE spendwise; GRANT CONNECT ON DATABASE ${db} TO \"{{name}}\";" \
    revocation_statements="REASSIGN OWNED BY \"{{name}}\" TO spendwise; DROP OWNED BY \"{{name}}\"; DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h \
    max_ttl=24h
done

# --- One least-privilege policy per service ------------------------------------
write_policy() {
  service="$1"
  with_db="$2"
  {
    echo "path \"secret/data/${service}\" { capabilities = [\"read\"] }"
    echo "path \"secret/data/${service}/*\" { capabilities = [\"read\"] }"
    if [ "${with_db}" = "yes" ]; then
      echo "path \"database/creds/${service}\" { capabilities = [\"read\"] }"
    fi
    echo "path \"sys/leases/renew\" { capabilities = [\"update\"] }"
    echo "path \"sys/leases/revoke\" { capabilities = [\"update\"] }"
    echo "path \"auth/token/renew-self\" { capabilities = [\"update\"] }"
    echo "path \"auth/token/lookup-self\" { capabilities = [\"read\"] }"
  } | vault policy write "${service}" -
}

write_policy user-service yes
write_policy transaction-service yes
write_policy budget-service yes
write_policy analytics-service no

# --- Dev tokens (Kubernetes auth replaces these in the cluster) -----------------
create_token() {
  service="$1"
  token_id="$2"
  vault token revoke "${token_id}" >/dev/null 2>&1 || true
  vault token create -id="${token_id}" -policy="${service}" -period=768h -orphan -display-name="${service}" >/dev/null
  echo "Issued dev token for ${service}"
}

create_token user-service "${USER_SERVICE_VAULT_TOKEN}"
create_token transaction-service "${TRANSACTION_SERVICE_VAULT_TOKEN}"
create_token budget-service "${BUDGET_SERVICE_VAULT_TOKEN}"
create_token analytics-service "${ANALYTICS_SERVICE_VAULT_TOKEN}"

echo "Vault seeded."
