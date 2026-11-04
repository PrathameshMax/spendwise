#!/usr/bin/env bash
# Secrets in Kubernetes — run once against the cluster's Vault (as an operator).
# Each service logs in to Vault with its own Kubernetes service-account token
# (spring.cloud.vault.authentication=KUBERNETES); Vault verifies it with the
# Kubernetes TokenReview API and hands back a token limited to that service's
# policy (the same policies infra/vault/init.sh writes in compose). No Vault
# token is ever stored in a manifest, image or Git.
set -euo pipefail

: "${VAULT_ADDR:?set VAULT_ADDR}"
: "${VAULT_TOKEN:?set VAULT_TOKEN (operator token)}"

vault auth list -format=json | grep -q '"kubernetes/"' || vault auth enable kubernetes

vault write auth/kubernetes/config \
  kubernetes_host="https://kubernetes.default.svc:443"

for service in user-service transaction-service budget-service analytics-service; do
  vault write "auth/kubernetes/role/${service}" \
    bound_service_account_names="${service}" \
    bound_service_account_namespaces=spendwise \
    policies="${service}" \
    ttl=1h
  echo "Kubernetes auth role ready for ${service}"
done

# Pods then run with:
#   VAULT_ENABLED=true  VAULT_URI=http://vault.vault.svc:8200  VAULT_AUTH_METHOD=KUBERNETES
# and the projected service-account token at
#   /var/run/secrets/kubernetes.io/serviceaccount/token
