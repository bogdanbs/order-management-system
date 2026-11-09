"""Checks that source files embedded in k8s ConfigMaps match their infrastructure copies.

Run from the repository root: python3 scripts/check-k8s-monitoring-sync.py
"""
import json
import sys
from pathlib import Path

# (ConfigMap manifest, data key, source file)
COPIES = [
    ("k8s/16-keycloak.yaml", "orders-realm.json", "infrastructure/keycloak/orders-realm.json"),
    ("k8s/02-configmaps.yaml", "prometheus.yml", "infrastructure/prometheus/prometheus.yml"),
    ("k8s/02-configmaps.yaml", "alerts.yml", "infrastructure/prometheus/alerts.yml"),
    ("k8s/02-configmaps.yaml", "alertmanager.yml", "infrastructure/alertmanager/alertmanager.yml"),
    ("k8s/14-grafana-dashboard.yaml", "order-outbox.json",
     "infrastructure/grafana/provisioning/dashboards/order-outbox.json"),
]


def embedded_block(manifest: str, key: str) -> str:
    """Returns the literal block under '  <key>: |', with its four-space indentation removed."""
    lines = Path(manifest).read_text(encoding="utf-8").splitlines()
    start = lines.index(f"  {key}: |") + 1
    block = []
    for line in lines[start:]:
        if line and not line.startswith("    "):
            break
        block.append(line[4:])
    return "\n".join(block).strip() + "\n"


def main() -> int:
    failed = False
    for manifest, key, source in COPIES:
        embedded = embedded_block(manifest, key)
        if key.endswith(".json"):
            json.loads(embedded)
        if embedded != Path(source).read_text(encoding="utf-8"):
            print(f"{manifest} [{key}] differs from {source}")
            failed = True
    if not failed:
        print("k8s monitoring ConfigMaps match their sources")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
