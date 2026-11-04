#!/usr/bin/env bash
# Generates the TLS material and admin client config for the secured Kafka overlay
# (docker-compose.kafka-secure.yml). Output: infra/kafka/secure/secrets/ (gitignored).
#
#   ca.crt / kafka.truststore.pem   dev CA every client trusts
#   kafka-N.keystore.pem            broker private key (PKCS#8) + certificate,
#                                   SAN = kafka-N, localhost, 127.0.0.1
#   admin.properties                SASL_SSL config for kafka-acls.sh
#
# Kafka reads PEM key/trust stores directly (ssl.*.type=PEM), so no keytool/JDK is
# needed. Run from Git Bash: bash infra/kafka/secure/generate-certs.sh
set -euo pipefail

# Git Bash would otherwise rewrite "/CN=..." into a Windows path.
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${SCRIPT_DIR}/secrets"
ADMIN_PASSWORD="${KAFKA_ADMIN_PASSWORD:-dev-kafka-admin-secret}"
DAYS=825

mkdir -p "${OUT}"
cd "${OUT}"

if [[ ! -f ca.key ]]; then
  openssl req -x509 -newkey rsa:4096 -nodes -sha256 -days "${DAYS}" \
    -keyout ca.key -out ca.crt -subj "/CN=SpendWise Dev Kafka CA"
fi
cp ca.crt kafka.truststore.pem

for broker in kafka-1 kafka-2 kafka-3; do
  openssl req -newkey rsa:2048 -nodes -sha256 \
    -keyout "${broker}.key" -out "${broker}.csr" -subj "/CN=${broker}"
  printf 'subjectAltName=DNS:%s,DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth,clientAuth\n' "${broker}" \
    > "${broker}.ext"
  openssl x509 -req -sha256 -days "${DAYS}" -in "${broker}.csr" \
    -CA ca.crt -CAkey ca.key -CAcreateserial -extfile "${broker}.ext" -out "${broker}.crt"
  cat "${broker}.key" "${broker}.crt" > "${broker}.keystore.pem"
  rm -f "${broker}.csr" "${broker}.ext"
done

cat > admin.properties <<EOF
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="${ADMIN_PASSWORD}";
ssl.truststore.type=PEM
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.pem
ssl.endpoint.identification.algorithm=https
EOF

chmod 600 ./*.key ./*.keystore.pem admin.properties
echo "Kafka TLS material written to ${OUT}"
