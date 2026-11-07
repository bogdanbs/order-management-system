# Order Management System — Kubernetes Deployment

All resources are deployed into the `orders` namespace.

## Prerequisites

- `kubectl` configured against a running cluster
- A local or cloud cluster: [minikube](https://minikube.sigs.k8s.io/), [kind](https://kind.sigs.k8s.io/), or any managed Kubernetes (EKS, GKE, AKS)
- Docker for building the application and Kafka Connect images

## 1. Install Strimzi

The Kafka manifests use Kafka 4.3 and the Strimzi `v1` API. Install Strimzi 1.3.0 (Kubernetes 1.32 or newer):

```bash
kubectl apply -f k8s/00-namespace.yaml
curl -fL -o /tmp/strimzi-crds.yaml \
  https://github.com/strimzi/strimzi-kafka-operator/releases/download/1.3.0/strimzi-crds-1.3.0.yaml
kubectl apply -f /tmp/strimzi-crds.yaml
curl -fL -o /tmp/strimzi-operator.yaml \
  https://github.com/strimzi/strimzi-kafka-operator/releases/download/1.3.0/strimzi-cluster-operator-1.3.0.yaml
sed 's/myproject/orders/g' /tmp/strimzi-operator.yaml | kubectl apply -n orders -f -
kubectl -n orders rollout status deployment/strimzi-cluster-operator
```

## 2. Build and load the images

Build both images from the project root:

```bash
docker build -t order-management-system-order-service:latest -f order-service/Dockerfile .
docker build -t orders-kafka-connect:1.0.0 -f Dockerfile.kafka-connect .
```

Load the images into the cluster.

**minikube:**
```bash
minikube image load order-management-system-order-service:latest
minikube image load orders-kafka-connect:1.0.0
```

**kind:**
```bash
kind load docker-image order-management-system-order-service:latest
kind load docker-image orders-kafka-connect:1.0.0
```

For a remote cluster, push both images to a registry and update the `image:` fields in `07-order-service.yaml` and `06-kafka-connect.yaml`.

## 3. Configure secrets and deploy

Create strong values locally; do not commit them. The checked-in secret template contains placeholders only.

```bash
export ORDERS_DB_PASSWORD='replace-with-a-strong-database-password'
export GRAFANA_ADMIN_PASSWORD='replace-with-a-strong-grafana-password'
export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/...'   # optional, for alert notifications

envsubst < k8s/01-secrets.yaml.example | kubectl apply -f -
```

The database and Grafana credentials and the Slack webhook URL are read from Kubernetes Secrets. Without a webhook URL, Alertmanager still runs and logs failed notification attempts. In CDC mode, the order service passes its database password from the Secret to Kafka Connect when registering the connector.

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

# Alertmanager → http://localhost:9093  (links in Slack messages point here)
kubectl -n orders port-forward svc/alertmanager 9093:9093
```

## 5. Outbox mode

The service starts in **polling** mode by default. To use Debezium CDC instead, run `kubectl -n orders set env deployment/order-service APP_OUTBOX_MODE=cdc`. The application registers its packaged connector configuration with Kafka Connect at startup; do not also create a `KafkaConnector` resource or register a second connector manually.

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
| alertmanager | — | 9093 |
| loki | — | 3100 |
