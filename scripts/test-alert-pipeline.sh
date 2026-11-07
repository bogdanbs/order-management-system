#!/usr/bin/env bash
# End-to-end check of the alert pipeline: the prometheus and alertmanager services from docker-compose.yml scrape a
# fake order-service reporting two FAILED outbox records, and the OutboxRecordsFailed alert must reach a fake Slack
# webhook with the message rendered from the real rule and Alertmanager config.
# Run from the repository root. Takes about 3 minutes: rule evaluation (1m) + "for: 1m" + group_wait (30s).
set -euo pipefail
export MSYS_NO_PATHCONV=1

compose=(docker compose -p alert-e2e -f docker-compose.yml -f scripts/alert-pipeline-e2e.compose.yml)
trap '"${compose[@]}" down -v --remove-orphans >/dev/null 2>&1' EXIT

# prometheus depends on alertmanager, so this starts the whole chain.
"${compose[@]}" up -d --quiet-pull prometheus fake-order-service fake-slack

message=""
for _ in $(seq 1 60); do
  message=$("${compose[@]}" logs --no-log-prefix fake-slack | grep '^SLACK ' || true)
  if [[ -n "$message" ]]; then
    break
  fi
  sleep 5
done

if [[ -z "$message" ]]; then
  echo "FAIL: no Slack notification received within 5 minutes" >&2
  "${compose[@]}" exec -T fake-slack python -c '
import urllib.request
for url in ("http://prometheus:9090/api/v1/targets?state=active", "http://prometheus:9090/api/v1/alerts"):
    print(url, urllib.request.urlopen(url, timeout=5).read().decode())
' >&2 || true
  "${compose[@]}" logs prometheus alertmanager >&2
  exit 1
fi

# The description comes from the Prometheus rule annotation with {{ $value }} rendered from the scraped gauge.
for expected in '"title":"[FIRING] OutboxRecordsFailed"' '"title_link":"http://localhost:9093/' \
                '*Outbox events failed to publish* (warning)\n2 outbox records are FAILED after exhausting their retries' \
                "Inspect outbox.outbox_events WHERE status = 'FAILED'."; do
  if [[ "$message" != *"$expected"* ]]; then
    echo "FAIL: notification lacks $expected" >&2
    echo "$message" >&2
    exit 1
  fi
done

echo "OK: OutboxRecordsFailed went from Prometheus through Alertmanager to Slack"
echo "$message"
