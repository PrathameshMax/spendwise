#!/usr/bin/env bash
# Usage: source scripts/smoke-env.sh
# Sets GW, TOKEN, AUTH, USER_ID, CATEGORY_ID, TXDATE for gateway smoke tests.
set +H
GW=http://localhost:8080
RND=$RANDOM
EMAIL="smoke-$RND@spendwise.dev"
PASS='ChangeMe123!'
TXDATE=$(date -u -d '1 day ago' +%F)
curl -s -o /dev/null -X POST $GW/api/v1/auth/register -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"rawPassword\":\"$PASS\"}"
TOKEN=$(curl -s -X POST $GW/api/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"rawPassword\":\"$PASS\"}" | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"
USER_ID=$(curl -s -X POST $GW/api/v1/users -H "$AUTH" -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"fullName\":\"Smoke Test\",\"preferredCurrency\":\"USD\"}" | jq -r .id)
CATEGORY_ID=$(curl -s -X POST $GW/api/v1/categories -H "$AUTH" -H "Content-Type: application/json" -d "{\"name\":\"Smoke-$RND\"}" | jq -r .id)
echo "TOKEN=${TOKEN:0:20}...  USER_ID=$USER_ID  CATEGORY_ID=$CATEGORY_ID  TXDATE=$TXDATE"
