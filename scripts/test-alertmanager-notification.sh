#!/usr/bin/env bash
# End-to-end check of the Slack notification path: starts the alertmanager service from docker-compose.yml with a
# fake Slack endpoint, posts an OutboxRecordsFailed alert and expects the rendered message at the webhook.
# Run from the repository root. Takes about 40 s because of the route's group_wait.
set -euo pipefail
export MSYS_NO_PATHCONV=1

compose=(docker compose -p am-e2e -f docker-compose.yml -f scripts/alertmanager-e2e.compose.yml)
trap '"${compose[@]}" down -v --remove-orphans >/dev/null 2>&1' EXIT

"${compose[@]}" up -d --quiet-pull alertmanager fake-slack

# Runs Python inside the fake-slack container, which shares the Compose network with Alertmanager.
on_network() {
  "${compose[@]}" exec -T fake-slack python -c "$1"
}

on_network '
import time, urllib.request
for _ in range(60):
    try:
        urllib.request.urlopen("http://alertmanager:9093/-/ready", timeout=2)
        break
    except Exception:
        time.sleep(1)
else:
    raise SystemExit("Alertmanager did not become ready")
'

on_network '
import json, urllib.request
alert = [{
    "labels": {"alertname": "OutboxRecordsFailed", "severity": "warning"},
    "annotations": {
        "summary": "Outbox events failed to publish",
        "description": "2 outbox records are FAILED after exhausting their retries.",
    },
}]
request = urllib.request.Request("http://alertmanager:9093/api/v2/alerts", data=json.dumps(alert).encode(),
                                 headers={"Content-Type": "application/json"})
urllib.request.urlopen(request, timeout=5)
'

for _ in $(seq 1 45); do
  message=$("${compose[@]}" logs --no-log-prefix fake-slack | grep '^SLACK ' || true)
  if [[ -n "$message" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "$message" ]]; then
  echo "FAIL: no Slack notification received" >&2
  "${compose[@]}" logs alertmanager >&2
  exit 1
fi

for expected in '"title":"[FIRING] OutboxRecordsFailed"' '"title_link":"http://localhost:9093/' \
                '*Outbox events failed to publish* (warning)\n2 outbox records are FAILED'; do
  if [[ "$message" != *"$expected"* ]]; then
    echo "FAIL: notification lacks $expected" >&2
    echo "$message" >&2
    exit 1
  fi
done

echo "OK: Slack notification received"
echo "$message"
