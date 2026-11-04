#!/usr/bin/env bash
# Usage: source scripts/smoke-env.sh [alice|bob|admin]
# Sets GW, KC, TOKEN, AUTH, USER_ID, CATEGORY_ID, TXDATE for gateway smoke tests.
#
# Tokens come from Keycloak with the dev-only "spendwise-dev-cli" client
# (password grant, enabled in the dev realm only). Browsers use the gateway's
# Authorization Code + PKCE login instead; Postman uses the spendwise-mobile
# client with PKCE. Seeded users are defined in infra/keycloak/spendwise-realm.json.
set +H
GW=http://localhost:8080
KC=http://localhost:8180/realms/spendwise/protocol/openid-connect
TXDATE=$(date -u -d '1 day ago' +%F)

token_for() {
  local username="$1" password="$2"
  curl -s -X POST "$KC/token" \
    -d grant_type=password \
    -d client_id=spendwise-dev-cli \
    -d scope=openid \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" | jq -r .access_token
}

case "${1:-alice}" in
  bob)   TOKEN=$(token_for bob@spendwise.dev 'Bob#Dev2026!x') ;;
  admin) TOKEN=$(token_for ops-admin@spendwise.dev 'OpsAdmin#Dev2026!') ;;
  *)     TOKEN=$(token_for alice@spendwise.dev 'Alice#Dev2026!') ;;
esac
AUTH="Authorization: Bearer $TOKEN"

# First call provisions the SpendWise profile; its id is the token's sub.
USER_ID=$(curl -s "$GW/api/v1/users/me" -H "$AUTH" | jq -r .id)

# Categories are an admin-managed catalogue: create one with the admin token.
ADMIN_TOKEN=$(token_for ops-admin@spendwise.dev 'OpsAdmin#Dev2026!')
CATEGORY_ID=$(curl -s -X POST "$GW/api/v1/categories" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d "{\"name\":\"Smoke-$RANDOM\"}" | jq -r .id)

echo "TOKEN=${TOKEN:0:20}...  USER_ID=$USER_ID  CATEGORY_ID=$CATEGORY_ID  TXDATE=$TXDATE"
