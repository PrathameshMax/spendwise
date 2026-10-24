#!/bin/sh
# One-shot Splunk bootstrap through the management REST API (port 8089):
#   index "spendwise": events kept 14 days, at most 5 GB on disk.
# Field extraction needs no setup: every event body is a JSON object and
# Splunk's default search-time extraction (KV_MODE=auto, AUTO_KV_JSON=true)
# turns its keys into fields.
# Safe to run again: an existing index is left as it is.
set -u

API=https://splunk:8089
AUTH="admin:${SPLUNK_PASSWORD}"

attempt=0
until curl -ks -o /dev/null -w '%{http_code}' -u "$AUTH" "$API/services/server/info" | grep -q 200; do
  attempt=$((attempt + 1))
  if [ "$attempt" -gt 60 ]; then
    echo "Splunk management API did not answer in time" >&2
    exit 1
  fi
  sleep 5
done

status=$(curl -ks -o /dev/null -w '%{http_code}' -u "$AUTH" "$API/services/data/indexes/spendwise")
if [ "$status" = "200" ]; then
  echo "Index 'spendwise' already exists"
else
  status=$(curl -ks -o /tmp/index.out -w '%{http_code}' -u "$AUTH" "$API/services/data/indexes" \
    -d name=spendwise \
    -d frozenTimePeriodInSecs=1209600 \
    -d maxTotalDataSizeMB=5120)
  if [ "$status" = "201" ] || [ "$status" = "409" ]; then
    echo "Created index 'spendwise'"
  else
    echo "Creating index 'spendwise' failed with HTTP $status:" >&2
    cat /tmp/index.out >&2
    exit 1
  fi
fi
