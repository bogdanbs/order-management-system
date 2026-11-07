# CLAUDE.md

Guidance for working in this repository.

## Project

Maven multi-module build (Java 21, Spring Boot 3.4). The only module is `order-service`, an
event-sourced order service with CQRS read models, a transactional outbox (polling or Debezium CDC),
and Kafka projections on PostgreSQL.

- `command/` – write side: API, commands, handler, `Order` aggregate and domain events
- `query/` – read side: projections, read-model repositories, query API, projection rebuild
- `infrastructure/` – event store, outbox, snapshots, upcasting, concurrency retry, Kafka config

## Build and test

```bash
mvn verify                      # unit + slice tests (Surefire) and integration tests (Failsafe)
mvn -pl order-service test      # unit + slice tests only, no Docker needed
```

Integration tests need a running Docker daemon (Testcontainers).

Monitoring config (CI job `monitoring`): Prometheus alert rules are tested with
`promtool test rules infrastructure/prometheus/alerts.test.yml`; Slack delivery through Alertmanager is
tested end to end with `bash scripts/test-alertmanager-notification.sh` (Docker, ~45 s). The k8s ConfigMaps
that embed Prometheus, Alertmanager and Grafana files must stay identical to their sources
(`python3 scripts/check-k8s-monitoring-sync.py`).

## Test conventions

| Kind | Suffix | Runs in | Scope |
|------|--------|---------|-------|
| Unit test | `*Test` | Surefire (`test`) | Plain JUnit/Mockito, no Spring context |
| Spring slice test | `*WebTest` | Surefire (`test`) | `@WebMvcTest` and other slices; collaborators mocked with `@MockitoBean` |
| Integration test | `*IT` | Failsafe (`integration-test`) | Real infrastructure via Testcontainers (PostgreSQL, Kafka) |

- Every change needs tests: new behaviour gets a test, bug fixes get a test that fails without the fix,
  removed behaviour takes its tests with it.
- Use `@MockitoBean` / `@MockitoSpyBean`, not the deprecated `@MockBean` / `@SpyBean`.
- Do not label slice tests as integration tests; `*IT` means real containers.
- Integration tests use `@ServiceConnection` containers and must not depend on a local
  Docker Compose stack.

## Commits

- Small, thematic commits: one concern per commit (rename, feature, CI, docs are separate commits).
- Each commit builds and passes `mvn verify` on its own.
- Imperative subject line, e.g. `Add outbox publisher integration test`.
