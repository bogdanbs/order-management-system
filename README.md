# Order Management System

A Spring Boot order service demonstrating event sourcing, an outbox, Kafka projections, PostgreSQL, and a Kubernetes deployment.

## Run locally

Requirements: Java 21 or later, Maven, and Docker Compose.

```bash
mvn -pl order-service test
docker compose up --build
```

The local Compose demo uses `orders_pass` for PostgreSQL and `admin` for Grafana when no environment variables are set. These are demo-only defaults. Set `ORDERS_DB_PASSWORD` and `GRAFANA_ADMIN_PASSWORD` before starting Compose to use different values. Grafana is available at <http://localhost:3001>; the order API is at <http://localhost:8090>.

## Order API

- `POST /api/orders` creates an order.
- `POST /api/orders/{id}/items` adds a product and quantity.
- `DELETE /api/orders/{id}/items/{productId}` removes one unit. The line remains until its quantity reaches zero.
- `POST /api/orders/{id}/submit` submits the order.
- `POST /api/orders/{id}/confirm-payment` confirms payment only when the amount exactly matches the order total. Repeating the same payment ID and amount is idempotent; another payment after confirmation is rejected.
- `POST /api/orders/{id}/submit-async` returns `501 Not Implemented`; no command consumer is configured. Use the synchronous submit endpoint.

Kafka projection processing retries failed events three times after the initial attempt, then publishes them to `order.events.dlq`. Database changes and the processed-event marker share a transaction; the consumer offset advances only after successful processing.

## Kubernetes

See [`k8s/README.md`](k8s/README.md) for image build, secret setup, deployment, and service access instructions. Create credentials from the checked-in `k8s/01-secrets.yaml.example` template; do not commit rendered secrets.
