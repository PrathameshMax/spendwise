#!/usr/bin/env bash
# Production Debugging Lab — one entry point for starting and stopping things,
# so you never have to remember docker compose file combinations.
#
#   ./scripts/lab.sh start            app stack in production mode (prod,lab profiles, JSON logs, /lab/seed on)
#   ./scripts/lab.sh start --build    same, rebuilding images first (after merging new code)
#   ./scripts/lab.sh stop             stop the app stack (containers and data kept)
#   ./scripts/lab.sh status           what is running, and is it healthy
#
#   ./scripts/lab.sh kibana-start     Elasticsearch + Kibana + Fluent Bit   -> http://localhost:5601
#   ./scripts/lab.sh kibana-stop      stop them (indexed logs kept)
#   ./scripts/lab.sh kibana-status    health, and is Fluent Bit shipping
#
#   ./scripts/lab.sh splunk-start     Splunk + Fluent Bit                   -> http://localhost:8000
#   ./scripts/lab.sh splunk-stop      stop them (indexed logs kept)
#   ./scripts/lab.sh splunk-status    health, and is Fluent Bit shipping
#
#   Run one log platform at a time (16 GB laptop). logs-start/stop/status = kibana-*.
#
# AppDynamics: when infra/appdynamics/appd.env exists (copy appd.env.example
# and fill in your controller details), "start" also attaches the AppDynamics
# Java agent to transaction-service, notification-service and api-gateway.
#
# Run from Git Bash anywhere inside the repository.
set -euo pipefail

cd "$(dirname "$0")/.."

APP=(docker compose -f docker-compose.yml -f docker-compose.lab.yml)
if [ -f infra/appdynamics/appd.env ]; then
  APP+=(-f docker-compose.appd.yml)
fi
ELK=(docker compose -f infra/elk/docker-compose.yml)
SPLUNK=(docker compose -f infra/splunk/docker-compose.yml)

usage() {
  sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

running() {
  [ -n "$(docker ps -q --filter "name=^$1\$")" ]
}

shipper_errors() {
  echo
  echo "Fluent Bit warnings/errors in the last 50 lines (none = logs are flowing):"
  docker logs --tail 50 "$1" 2>&1 | grep -E "\[(error|warn)\]" || echo "  none"
}

case "${1:-}" in
  start)
    if [ -f infra/appdynamics/appd.env ]; then
      echo "AppDynamics agent: ON (infra/appdynamics/appd.env found)"
    else
      echo "AppDynamics agent: off (no infra/appdynamics/appd.env)"
    fi
    if [ "${2:-}" = "--build" ]; then
      "${APP[@]}" up -d --build
    else
      "${APP[@]}" up -d
    fi
    echo
    echo "Started in production mode. Services take 1-3 minutes to become healthy:"
    echo "  ./scripts/lab.sh status"
    ;;
  stop)
    "${APP[@]}" stop
    ;;
  status)
    "${APP[@]}" ps --format "table {{.Service}}\t{{.Status}}"
    ;;
  kibana-start|logs-start)
    if running spendwise-splunk; then
      echo "Splunk is running. Stop it first (./scripts/lab.sh splunk-stop): one log platform at a time." >&2
      exit 1
    fi
    "${ELK[@]}" up -d
    echo
    echo "Kibana needs 1-2 minutes on first start: http://localhost:5601"
    echo "  ./scripts/lab.sh kibana-status"
    ;;
  kibana-stop|logs-stop)
    "${ELK[@]}" stop
    ;;
  kibana-status|logs-status)
    "${ELK[@]}" ps -a --format "table {{.Service}}\t{{.Status}}"
    shipper_errors spendwise-fluent-bit
    ;;
  splunk-start)
    if running spendwise-elasticsearch; then
      echo "Elasticsearch/Kibana are running. Stop them first (./scripts/lab.sh kibana-stop): one log platform at a time." >&2
      exit 1
    fi
    "${SPLUNK[@]}" up -d
    echo
    echo "Splunk needs 3-5 minutes on first start (longer while the image downloads): http://localhost:8000"
    echo "Login: admin / SpendWiseLab#2026"
    echo "  ./scripts/lab.sh splunk-status"
    ;;
  splunk-stop)
    "${SPLUNK[@]}" stop
    ;;
  splunk-status)
    "${SPLUNK[@]}" ps -a --format "table {{.Service}}\t{{.Status}}"
    shipper_errors spendwise-fluent-bit-splunk
    ;;
  *)
    usage
    ;;
esac
