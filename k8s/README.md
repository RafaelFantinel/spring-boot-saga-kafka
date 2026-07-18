# Kubernetes (minikube) deployment

Runs the full reference architecture on a local minikube cluster: Postgres,
Kafka (KRaft), Zipkin, Prometheus, Grafana and the five Spring Boot services.

## Requirements

- `docker`, `minikube`, `kubectl` in `PATH` (or `~/.local/bin`).
- **RAM**: the full stack is tight on a 7–8 GB host. On WSL2 set the VM limit
  in `C:\Users\<user>\.wslconfig` (leave ~2 GB for Windows; swap absorbs
  spikes):

  ```ini
  [wsl2]
  memory=5GB
  processors=6
  swap=6GB
  ```

  Then `wsl --shutdown` (PowerShell) and reopen the terminal. `deploy.sh`
  starts minikube with `--memory=4096` to fit inside the 5 GB VM. On a bigger
  host, raise both values.

## Deploy

```bash
./k8s/deploy.sh
```

What it does: starts minikube (docker driver), builds the 5 service images
**inside** minikube's docker daemon (no registry needed), generates ConfigMaps
from the existing repo files (`postgres-init/`, `grafana/provisioning/*`),
applies all manifests, and waits for every rollout.

## Endpoints (NodePort on `minikube ip`)

| Service    | URL                        |
|------------|----------------------------|
| order-service | `http://<ip>:30081/orders` |
| Grafana    | `http://<ip>:30300` (admin/admin) |
| Prometheus | `http://<ip>:30090`        |
| Zipkin     | `http://<ip>:30411`        |

`<ip> = minikube ip`. The flow dashboard: `http://<ip>:30300/d/refarch-flow-e2e`.

## Smoke test

```bash
IP=$(minikube ip)
curl -s -X POST http://$IP:30081/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"c1","sku":"SKU-1","quantity":2,"amount":49.90}'
# poll status until COMPLETED
```

## Teardown

```bash
./k8s/teardown.sh            # remove namespace (keep cluster)
./k8s/teardown.sh --cluster  # also minikube delete
```

## Notes / deltas vs docker-compose

- Kafka KRaft controller quorum uses `0@localhost:9093` (not `kafka:9093`):
  in k8s a Service only publishes endpoints for **Ready** pods, so a self-
  reference through the Service would deadlock startup.
- Service DNS names match the compose service names, so app env vars
  (`postgres`, `kafka:9092`, `zipkin`, `flaky-external-service:8090`) are
  unchanged.
- JVM heap capped via `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` + memory
  limits to fit several JVMs on a laptop.
- initContainers gate each app on `postgres`/`kafka` TCP reachability.
- Images use `imagePullPolicy: IfNotPresent` with tag `:local` — built into
  minikube's docker, never pulled from a registry.
