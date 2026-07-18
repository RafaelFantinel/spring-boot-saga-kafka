#!/usr/bin/env bash
# Deploy the full reference architecture onto local minikube.
# Requires: docker, minikube, kubectl (in PATH or ~/.local/bin).
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SERVICES=(order-service saga-orchestrator payment-service inventory-service flaky-external-service)

echo "==> 1/6 minikube cluster"
if ! minikube status >/dev/null 2>&1; then
  minikube start --driver=docker --cpus=4 --memory=4096
fi

echo "==> 2/6 build images into minikube docker"
eval "$(minikube docker-env)"
for s in "${SERVICES[@]}"; do
  echo "    build refarch/$s:local"
  docker build -q -t "refarch/$s:local" -f "$s/Dockerfile" .
done
eval "$(minikube docker-env -u)"

echo "==> 3/6 namespace + config (generated from repo files)"
kubectl apply -f k8s/00-namespace.yaml
kubectl -n refarch create configmap postgres-init \
  --from-file=postgres-init/ --dry-run=client -o yaml | kubectl apply -f -
kubectl -n refarch create configmap grafana-datasources \
  --from-file=grafana/provisioning/datasources/ --dry-run=client -o yaml | kubectl apply -f -
kubectl -n refarch create configmap grafana-dashboards \
  --from-file=grafana/provisioning/dashboards/ --dry-run=client -o yaml | kubectl apply -f -

echo "==> 4/6 apply workloads"
kubectl apply -f k8s/

echo "==> 5/6 wait for rollouts (infra first)"
kubectl -n refarch rollout status deploy/postgres --timeout=180s
kubectl -n refarch rollout status deploy/kafka    --timeout=300s
for d in zipkin flaky-external-service order-service saga-orchestrator \
         payment-service inventory-service prometheus grafana; do
  kubectl -n refarch rollout status deploy/$d --timeout=300s
done

IP="$(minikube ip)"
echo "==> 6/6 done. Endpoints:"
cat <<EOF

  order-service : http://$IP:30081   (POST /orders)
  grafana       : http://$IP:30300   (admin/admin, flow dashboard: /d/refarch-flow-e2e)
  prometheus    : http://$IP:30090
  zipkin        : http://$IP:30411

  Smoke test:
    curl -s -X POST http://$IP:30081/orders -H 'Content-Type: application/json' \\
      -d '{"customerId":"c1","sku":"SKU-1","quantity":2,"amount":49.90}'
EOF
