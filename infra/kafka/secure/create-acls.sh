#!/bin/bash
# Topic ACLs for the secured Kafka overlay — run by the one-shot "kafka-acl-init"
# container. With StandardAuthorizer and allow.everyone.if.no.acl.found=false,
# a principal can do nothing that is not listed here (least privilege per service).
set -euo pipefail

ACLS=/opt/kafka/bin/kafka-acls.sh
BOOTSTRAP=kafka-1:19092
ADMIN_CONFIG=/etc/kafka/secrets/admin.properties

acl() {
  "${ACLS}" --bootstrap-server "${BOOTSTRAP}" --command-config "${ADMIN_CONFIG}" --add --force "$@"
}

echo "Waiting for the secured cluster ..."
until "${ACLS}" --bootstrap-server "${BOOTSTRAP}" --command-config "${ADMIN_CONFIG}" --list >/dev/null 2>&1; do
  sleep 3
done

# transaction-service: publishes transaction-events (outbox relay, idempotent
# producer), consumes budget-events (saga decisions), may dead-letter.
acl --allow-principal User:svc_transaction --operation Write --operation Describe --topic transaction-events
acl --allow-principal User:svc_transaction --operation IdempotentWrite --cluster
acl --allow-principal User:svc_transaction --operation Read --operation Describe --topic budget-events
acl --allow-principal User:svc_transaction --operation Write --operation Describe --topic budget-events.DLT
acl --allow-principal User:svc_transaction --operation Read --group transaction-service
acl --allow-principal User:svc_transaction --operation Create --operation Describe --operation DescribeConfigs --topic transaction-events --topic budget-events

# budget-service: consumes transaction-events, publishes budget-events.
acl --allow-principal User:svc_budget --operation Read --operation Describe --topic transaction-events
acl --allow-principal User:svc_budget --operation Write --operation Describe --topic budget-events
acl --allow-principal User:svc_budget --operation IdempotentWrite --cluster
acl --allow-principal User:svc_budget --operation Write --operation Describe --topic transaction-events.DLT
acl --allow-principal User:svc_budget --operation Read --group budget-service

# notification-service and analytics-service: read-only consumers.
for principal in svc_notification:notification-service svc_analytics:analytics-service; do
  user="${principal%%:*}"
  group="${principal##*:}"
  acl --allow-principal "User:${user}" --operation Read --operation Describe --topic transaction-events
  acl --allow-principal "User:${user}" --operation Write --operation Describe --topic transaction-events.DLT
  acl --allow-principal "User:${user}" --operation Read --group "${group}"
done
acl --allow-principal User:svc_notification --operation Read --operation Describe --topic budget-events

# schema-registry: owns the _schemas topic.
acl --allow-principal User:schema_registry --operation All --topic _schemas
acl --allow-principal User:schema_registry --operation All --group schema-registry
acl --allow-principal User:schema_registry --operation Describe --operation DescribeConfigs --cluster

echo "ACLs applied:"
"${ACLS}" --bootstrap-server "${BOOTSTRAP}" --command-config "${ADMIN_CONFIG}" --list
