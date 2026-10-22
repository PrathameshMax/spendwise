#!/bin/sh
# One-shot Kibana bootstrap: creates the "SpendWise logs" data view over
# spendwise-logs-* and makes it the default. Safe to run again: an existing
# data view with the same id is left as it is.
set -u

KIBANA=http://kibana:5601
VIEW_ID=spendwise-logs

attempt=0
until curl -fs "$KIBANA/api/status" | grep -Eq '"level": *"available"'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -gt 60 ]; then
    echo "Kibana did not become available in time" >&2
    exit 1
  fi
  sleep 5
done

status=$(curl -s -o /dev/null -w '%{http_code}' "$KIBANA/api/data_views/data_view/$VIEW_ID")
if [ "$status" = "200" ]; then
  echo "Data view '$VIEW_ID' already exists"
else
  curl -fs -X POST "$KIBANA/api/data_views/data_view" \
    -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
    -d "{\"data_view\":{\"id\":\"$VIEW_ID\",\"title\":\"spendwise-logs-*\",\"name\":\"SpendWise logs\",\"timeFieldName\":\"@timestamp\"}}" \
    > /dev/null && echo "Created data view '$VIEW_ID'"
fi

curl -fs -X POST "$KIBANA/api/data_views/default" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d "{\"data_view_id\":\"$VIEW_ID\",\"force\":true}" > /dev/null && echo "Set '$VIEW_ID' as the default data view"
