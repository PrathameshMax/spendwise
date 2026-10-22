#!/usr/bin/env bash
# Production Debugging Lab — one entry point for starting and stopping things,
# so you never have to remember docker compose file combinations.
#
#   ./scripts/lab.sh start            app stack in production mode (prod profile, JSON logs, /lab/seed on)
#   ./scripts/lab.sh start --build    same, rebuilding images first (after merging new code)
#   ./scripts/lab.sh stop             stop the app stack (containers and data kept)
#   ./scripts/lab.sh status           what is running, and is it healthy
#
#   ./scripts/lab.sh logs-start       start Elasticsearch + Kibana + Fluent Bit (Kibana: http://localhost:5601)
#   ./scripts/lab.sh logs-stop        stop them (indexed logs kept)
#   ./scripts/lab.sh logs-status      are they running, and is Fluent Bit shipping
#
# Run from Git Bash anywhere inside the repository.
set -euo pipefail

cd "$(dirname "$0")/.."

APP=(docker compose -f docker-compose.yml -f docker-compose.lab.yml)
ELK=(docker compose -f infra/elk/docker-compose.yml)

usage() {
  sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

case "${1:-}" in
  start)
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
  logs-start)
    "${ELK[@]}" up -d
    echo
    echo "Kibana needs 1-2 minutes on first start: http://localhost:5601"
    echo "  ./scripts/lab.sh logs-status"
    ;;
  logs-stop)
    "${ELK[@]}" stop
    ;;
  logs-status)
    "${ELK[@]}" ps -a --format "table {{.Service}}\t{{.Status}}"
    echo
    echo "Last Fluent Bit messages (errors here mean logs are not reaching Elasticsearch):"
    docker logs --tail 5 spendwise-fluent-bit 2>&1 || true
    ;;
  *)
    usage
    ;;
esac
