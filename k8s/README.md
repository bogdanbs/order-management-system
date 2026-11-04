# Order Management System — Kubernetes Deployment

All resources are deployed into the `orders` namespace.

## Prerequisites

- `kubectl` configured against a running cluster
- A local or cloud cluster: [minikube](https://minikube.sigs.k8s.io/), [kind](https://kind.sigs.k8s.io/), or any managed Kubernetes (EKS, GKE, AKS)

## 1. Build the application image

```bash
cd order-management-system
mvn -pl order-service spring-boot:build-image \
  -Dspring-boot.build-image.imageName=order-management-system-order-service:latest
```

This produces the image `order-management-system-order-service:latest`.

## 2. Load the image into the cluster

**minikube:**
```bash
minikube image load order-management-system-order-service:latest
```

**kind:**
```bash
kind load docker-image order-management-system-order-service:latest
```

**Cloud registry (ECR, GCR, ACR, Docker Hub):**
```bash
docker tag order-management-system-order-service:latest <registry>/order-management-system-order-service:latest
docker push <registry>/order-management-system-order-service:latest
```

Then update the `image:` field in `07-order-service.yaml` to point to your registry image.

## 3. Configure secrets and deploy

Create strong values locally; do not commit them. The checked-in secret template contains placeholders only.

```bash
export ORDERS_DB_PASSWORD='replace-with-a-strong-database-password'
export GRAFANA_ADMIN_PASSWORD='replace-with-a-strong-grafana-password'

kubectl apply -f k8s/00-namespace.yaml
envsubst < k8s/01-secrets.yaml.example | kubectl apply -f -
```

The database and Grafana credentials are read from Kubernetes Secrets. Kafka Connect resolves the database password from the `orders-db` Secret using Strimzi's Kubernetes Secret Config Provider.

```bash
kubectl apply -f k8s/
kubectl -n orders rollout status deployment/order-service
```

Watch all pods come up:
```bash
kubectl -n orders get pods -w
```

## 4. Access the services

### minikube (NodePort)

```bash
# Order Service REST API
minikube service order-service -n orders --url

# Grafana dashboards
minikube service grafana -n orders --url

# Jaeger tracing UI
minikube service jaeger -n orders --url
```

### Port-forward (any cluster)

```bash
# Order Service REST API → http://localhost:8090
kubectl -n orders port-forward svc/order-service 8090:8080

# Grafana → http://localhost:3001  (user: admin, password: GRAFANA_ADMIN_PASSWORD)
kubectl -n orders port-forward svc/grafana 3001:3000

# Jaeger UI → http://localhost:16686
kubectl -n orders port-forward svc/jaeger 16686:16686

# Prometheus → http://localhost:9090
kubectl -n orders port-forward svc/prometheus 9090:9090
```

## 5. Outbox mode

The service starts in **polling** mode by default. To switch to CDC (Debezium):

```bash
kubectl -n orders set env deployment/order-service APP_OUTBOX_MODE=cdc
```

Kafka Connect will register the Debezium connector automatically on startup. To register it manually:

```bash
kubectl -n orders port-forward svc/kafka-connect 8083:8083 &
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @k8s/debezium-pg-connector.json
```

## 6. Projection rebuild

If the read-model gets out of sync, trigger a full rebuild:

```bash
curl -X POST http://localhost:8090/admin/projections/rebuild
```

(Assumes `order-service` is port-forwarded to 8090 as above.)

## 7. Teardown

Deleting the namespace removes every resource, including PVCs (and their data):

```bash
kubectl delete namespace orders
```

To keep persistent data (Postgres, Kafka, Jaeger, Loki, Grafana), delete the workloads without deleting the namespace and PVCs:

```bash
kubectl -n orders delete deployments,statefulsets,services,configmaps,secrets --all
```

## Port reference

| Service | NodePort | Internal port |
|---|---|---|
| order-service | 30090 | 8080 |
| jaeger UI | 30686 | 16686 |
| grafana | 30300 | 3000 |
| postgres | — | 5432 |
| kafka | — | 9092 |
| kafka-connect | — | 8083 |
| prometheus | — | 9090 |
| loki | — | 3100 |
