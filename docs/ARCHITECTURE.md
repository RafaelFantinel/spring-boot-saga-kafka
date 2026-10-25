# Architecture

Complete architectural view of the reference system: context, components,
message contracts, runtime flows, resilience, observability and deployment
topologies. Every element below maps to real code in this repo - class names,
topics, ports and config values are taken from the source, not idealized.

For the "why" behind each pattern, see the [README](../README.md#why-these-patterns).

---

## 1. System context

Who talks to the system, and what it depends on.

```mermaid
flowchart LR
    USER["Operator / client<br/>curl · Swagger UI"]

    subgraph SYS["Order fulfillment system"]
        API["order-service<br/>REST intake :8081"]
        CORE["saga-orchestrator<br/>payment-service<br/>inventory-service"]
    end

    BANK["flaky-external-service :8090<br/>simulated external bank"]
    OBS["Observability<br/>Prometheus · Grafana · Zipkin"]

    USER -->|"POST /orders<br/>GET /orders/{id}"| API
    API --- CORE
    CORE -->|HTTP POST /charge| BANK
    SYS -.->|"metrics · traces"| OBS
    BANK -.->|"metrics · traces"| OBS
```

The bank is *inside* the repo but conceptually external: it is the untrusted,
unreliable dependency the resilience patterns exist to contain.

---

## 2. Component architecture

The full picture: services, their internal collaborators, their databases, and
every Kafka topic between them.

```mermaid
flowchart TB
    CLIENT["Client<br/>curl · Swagger UI"]

    subgraph OS["order-service :8081"]
        OC["OrderController"]
        OSVC["OrderService<br/>@Transactional"]
        OPOLL["OutboxPoller<br/>@Scheduled 500ms"]
        OLIST["OrderEventsListener"]
        ODB[("orders_db<br/>orders · outbox_events")]
    end

    subgraph SO["saga-orchestrator :8082"]
        SOL["OrderEventsListener"]
        SPL["PaymentEventsListener"]
        SIL["InventoryEventsListener"]
        SSVC["SagaOrchestratorService"]
        SSM["SagaStateMachine<br/>transition table"]
        SPUB["MessagePublisher"]
        SREC["SagaRecoveryRunner<br/>ApplicationRunner"]
        SDB[("saga_db<br/>saga_instance")]
    end

    subgraph PS["payment-service :8083"]
        PCL["PaymentCommandsListener"]
        PBANK["BankClient<br/>CircuitBreaker · Retry · TimeLimiter"]
        PPUB["PaymentEventPublisher"]
    end

    subgraph IS["inventory-service :8084"]
        ICL["InventoryCommandsListener"]
        IRES["InventoryReservationService<br/>retry on optimistic lock, max 5"]
        IOPS["InventoryTransactionalOps"]
        IPUB["InventoryEventPublisher"]
        IDB[("inventory_db<br/>inventory @Version")]
    end

    FLAKY["flaky-external-service :8090<br/>POST /charge<br/>~40% fail or stall"]

    T1{{"order-events"}}
    T2{{"payment-commands"}}
    T3{{"payment-events"}}
    T4{{"inventory-commands"}}
    T5{{"inventory-events"}}

    CLIENT -->|POST /orders| OC
    OC --> OSVC
    OSVC -->|"order + outbox row<br/>one transaction"| ODB
    ODB --> OPOLL
    OPOLL -->|OrderCreated| T1

    T1 --> SOL
    SOL --> SSVC
    SPL --> SSVC
    SIL --> SSVC
    SSVC <-->|"read step, apply trigger"| SSM
    SSVC -->|"persist every transition"| SDB
    SREC -.->|"scan non-terminal sagas on boot"| SDB
    SSVC --> SPUB

    SPUB -->|"ReservePayment<br/>ReleasePayment"| T2
    SPUB -->|ReserveInventory| T4
    SPUB -->|"OrderCompleted<br/>OrderFailed"| T1

    T2 --> PCL
    PCL --> PBANK
    PBANK -->|HTTP| FLAKY
    PBANK --> PPUB
    PPUB -->|"PaymentReserved<br/>PaymentFailed<br/>PaymentReleased"| T3
    T3 --> SPL

    T4 --> ICL
    ICL --> IRES
    IRES --> IOPS
    IOPS <--> IDB
    IRES --> IPUB
    IPUB -->|"InventoryReserved<br/>InventoryFailed"| T5
    T5 --> SIL

    T1 --> OLIST
    OLIST -->|"update status<br/>COMPLETED / FAILED"| ODB
    CLIENT -->|"GET /orders/{id}"| OC
```

**Database-per-service.** Three logical databases (`orders_db`, `saga_db`,
`inventory_db`), each written only by its owning service. payment-service is
stateless - it owns no database, because a payment reservation here is a call
to the bank plus an event, with no local state worth persisting.

---

## 3. Message contracts

```mermaid
flowchart LR
    subgraph P["Producers"]
        p1["order-service"]
        p2["saga-orchestrator"]
        p3["payment-service"]
        p4["inventory-service"]
    end

    t1{{"order-events"}}
    t2{{"payment-commands"}}
    t3{{"payment-events"}}
    t4{{"inventory-commands"}}
    t5{{"inventory-events"}}

    subgraph C["Consumers (group.id = service name)"]
        c1["order-service"]
        c2["saga-orchestrator"]
        c3["payment-service"]
        c4["inventory-service"]
    end

    p1 -->|OrderCreated| t1
    p2 -->|"OrderCompleted · OrderFailed"| t1
    t1 --> c2
    t1 --> c1

    p2 -->|"ReservePayment · ReleasePayment"| t2
    t2 --> c3
    p3 -->|"PaymentReserved · PaymentFailed · PaymentReleased"| t3
    t3 --> c2

    p2 -->|ReserveInventory| t4
    t4 --> c4
    p4 -->|"InventoryReserved · InventoryFailed"| t5
    t5 --> c2
```

| Topic | Producers | Consumers | Payloads |
|---|---|---|---|
| `order-events` | order-service, saga-orchestrator | saga-orchestrator, order-service | `OrderCreated`, `OrderCompleted`, `OrderFailed` |
| `payment-commands` | saga-orchestrator | payment-service | `ReservePayment`, `ReleasePayment` |
| `payment-events` | payment-service | saga-orchestrator | `PaymentReserved`, `PaymentFailed`, `PaymentReleased` |
| `inventory-commands` | saga-orchestrator | inventory-service | `ReserveInventory`, `ReleaseInventory` (implemented, unused) |
| `inventory-events` | inventory-service | saga-orchestrator | `InventoryReserved`, `InventoryFailed` |

Commands are imperative and addressed to one owner; events are facts announced
to whoever cares. Each service declares its own copy of the record types it
consumes - no shared library, so no lockstep deploys.

---

## 4. Happy path

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant O as order-service
    participant DB as orders_db
    participant K as Kafka
    participant S as saga-orchestrator
    participant SDB as saga_db
    participant P as payment-service
    participant B as bank
    participant I as inventory-service

    C->>O: POST /orders
    activate O
    O->>DB: INSERT order + outbox_event (one tx)
    O-->>C: 201 Created, status=CREATED
    deactivate O

    Note over O,DB: response returns before the saga runs

    O->>DB: OutboxPoller reads unpublished
    O->>K: publish OrderCreated -> order-events
    O->>DB: mark published (after broker ack)

    K->>S: OrderCreated
    S->>SDB: SagaInstance STARTED -> PAYMENT_REQUESTED
    S->>K: ReservePayment -> payment-commands

    K->>P: ReservePayment
    P->>B: POST /charge
    B-->>P: 200 APPROVED
    P->>K: PaymentReserved -> payment-events

    K->>S: PaymentReserved
    S->>SDB: PAYMENT_REQUESTED -> INVENTORY_REQUESTED
    S->>K: ReserveInventory -> inventory-commands

    K->>I: ReserveInventory
    I->>I: decrement stock (optimistic lock)
    I->>K: InventoryReserved -> inventory-events

    K->>S: InventoryReserved
    S->>SDB: INVENTORY_REQUESTED -> COMPLETED
    S->>K: OrderCompleted -> order-events

    K->>O: OrderCompleted
    O->>DB: status = COMPLETED
    C->>O: GET /orders/{id}
    O-->>C: status=COMPLETED
```

---

## 5. Compensation path

Inventory is the last step, so a failure there means payment already succeeded
and must be undone. This is what makes it a saga rather than a pipeline.

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka
    participant S as saga-orchestrator
    participant SDB as saga_db
    participant P as payment-service
    participant I as inventory-service
    participant O as order-service

    Note over I: e.g. SKU-LOW-STOCK, quantity 5, only 1 in stock

    K->>I: ReserveInventory
    I->>K: InventoryFailed (insufficient_stock)
    K->>S: InventoryFailed
    S->>SDB: INVENTORY_REQUESTED -> COMPENSATING_PAYMENT
    S->>K: ReleasePayment -> payment-commands

    K->>P: ReleasePayment
    P->>K: PaymentReleased -> payment-events
    K->>S: PaymentReleased
    S->>SDB: COMPENSATING_PAYMENT -> FAILED
    S->>K: OrderFailed -> order-events
    K->>O: OrderFailed
    O->>O: status = FAILED
```

A payment failure short-circuits earlier: `PAYMENT_REQUESTED --PAYMENT_FAILED-->
FAILED`, with nothing to compensate because inventory was never touched.

---

## 6. Saga state machine

Six states, six declared transitions. Anything not in the table raises
`IllegalStateException` - which is how duplicate Kafka deliveries get rejected
instead of double-applied.

```mermaid
stateDiagram-v2
    [*] --> STARTED
    STARTED --> PAYMENT_REQUESTED: ORDER_CREATED / send ReservePayment
    PAYMENT_REQUESTED --> INVENTORY_REQUESTED: PAYMENT_RESERVED / send ReserveInventory
    PAYMENT_REQUESTED --> FAILED: PAYMENT_FAILED / publish OrderFailed
    INVENTORY_REQUESTED --> COMPLETED: INVENTORY_RESERVED / publish OrderCompleted
    INVENTORY_REQUESTED --> COMPENSATING_PAYMENT: INVENTORY_FAILED / send ReleasePayment
    COMPENSATING_PAYMENT --> FAILED: PAYMENT_RELEASED / publish OrderFailed
    COMPLETED --> [*]
    FAILED --> [*]

    note right of COMPENSATING_PAYMENT
        Only reachable when payment
        succeeded and inventory did not
    end note
```

**Crash recovery.** State lives in `saga_instance`, never only in memory. Each
incoming event re-reads the row by `orderId` before applying a trigger, so a
`kill -9` mid-saga loses nothing: the next event resumes from the durable step.
`SagaRecoveryRunner` logs every non-terminal saga at startup.

---

## 7. Transactional outbox

The dual-write problem and how the outbox removes it.

```mermaid
flowchart LR
    subgraph TX["One database transaction"]
        A["INSERT INTO orders"]
        B["INSERT INTO outbox_events<br/>published = false"]
    end

    C["OutboxPoller<br/>@Scheduled 500ms"]
    D["Kafka order-events"]
    E["UPDATE outbox_events<br/>published = true"]

    A --- B
    TX -->|"commits atomically"| C
    C -->|"read unpublished"| C
    C -->|"send"| D
    D -->|"broker ack"| E
```

Both rows commit or neither does - no window where an order exists with no
event. A crash between publish and the `published = true` update replays the
event, so delivery is **at-least-once**, never at-most-once. Consumers absorb
the duplicate via the state machine's transition guard.

---

## 8. Resilience around the bank

```mermaid
flowchart LR
    IN["PaymentCommandsListener"]
    TL["TimeLimiter<br/>1200ms"]
    CB["CircuitBreaker 'bankCharge'"]
    RT["Retry<br/>3 attempts, 200ms exp backoff"]
    HTTP["RestClient -> POST /charge"]
    FB["fallback<br/>PaymentFailed"]

    IN --> TL --> CB --> RT --> HTTP
    CB -->|"OPEN: fail fast,<br/>no network call"| FB
    TL -->|timeout| FB
    RT -->|"attempts exhausted"| FB
```

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: failure rate >= 50%<br/>over 10-call window<br/>(min 5 calls)
    OPEN --> HALF_OPEN: after 10s<br/>automatic transition
    HALF_OPEN --> CLOSED: 3 probe calls succeed
    HALF_OPEN --> OPEN: probe fails
```

Config lives in `payment-service/src/main/resources/application.yml`:

| Knob | Value | Effect |
|---|---|---|
| `sliding-window-size` | 10 (count-based) | Decision window |
| `minimum-number-of-calls` | 5 | Below this, breaker stays closed |
| `failure-rate-threshold` | 50% | Trip point |
| `slow-call-duration-threshold` | 1500ms | Slow calls count as failures |
| `wait-duration-in-open-state` | 10s | Recovery window before probing |
| `permitted-number-of-calls-in-half-open-state` | 3 | Probe budget |
| TimeLimiter `timeout-duration` | 1200ms | Hard ceiling per call |
| Retry `max-attempts` | 3 | Absorbs transient blips pre-breaker |

The bank stalls for 3000ms on its slow path, well past both the 1200ms time
limit and the 1500ms slow-call threshold - stalls are guaranteed to register as
failures, which is what makes the breaker trip reproducibly.

---

## 9. Observability

```mermaid
flowchart TB
    subgraph APPS["Services (Spring Boot Actuator + Micrometer)"]
        A1["order-service:8081"]
        A2["saga-orchestrator:8082"]
        A3["payment-service:8083"]
        A4["inventory-service:8084"]
        A5["flaky-external-service:8090"]
    end

    PROM["Prometheus :9090<br/>scrape /actuator/prometheus every 5s"]
    GRAF["Grafana :3000<br/>6 provisioned dashboards"]
    ZIP["Zipkin :9411<br/>100% sampling"]
    LOGS["Structured logs<br/>traceId · spanId in MDC"]

    APPS -->|"pull metrics"| PROM
    PROM --> GRAF
    APPS -->|"push spans"| ZIP
    APPS --> LOGS
```

Trace context propagates across the whole flow - HTTP into order-service, then
through Kafka headers into every consumer - so one order is a single Zipkin
trace spanning all services. The same `traceId` appears in the JSON logs, which
is how you jump from a Grafana spike to the exact request.

| Dashboard | UID |
|---|---|
| Reference Architecture Overview | `refarch-overview` |
| Saga Flow - End to End | `refarch-flow-e2e` |
| Payment Resilience | `refarch-resilience` |
| Kafka Messaging | `refarch-kafka` |
| HTTP API | `refarch-http` |
| JVM & Runtime | `refarch-jvm` |

---

## 10. Deployment topologies

The same five services run in two environments. Application config is identical
because the k8s Service names deliberately match the compose service names.

### docker-compose

```mermaid
flowchart TB
    subgraph NET["docker network: refarch"]
        PG[("postgres:16<br/>orders_db · saga_db · inventory_db<br/>:5432")]
        KAF["kafka KRaft<br/>no Zookeeper<br/>:9092"]
        Z["zipkin :9411"]
        PR["prometheus :9090"]
        GR["grafana :3000"]
        S1["order-service :8081"]
        S2["saga-orchestrator :8082"]
        S3["payment-service :8083"]
        S4["inventory-service :8084"]
        S5["flaky-external-service :8090"]
    end

    S1 --> PG
    S2 --> PG
    S4 --> PG
    S1 --> KAF
    S2 --> KAF
    S3 --> KAF
    S4 --> KAF
    S3 -->|HTTP| S5
    PR --> S1 & S2 & S3 & S4 & S5
    GR --> PR
```

Startup ordering is enforced with health checks: Postgres reports healthy only
after `init-databases.sh` has created all three databases, so no service ever
boots against a half-initialized cluster.

### Kubernetes (minikube)

```mermaid
flowchart TB
    subgraph NS["namespace: refarch"]
        subgraph DATA["Stateful"]
            KPG[("postgres<br/>PVC")]
            KKAF["kafka KRaft<br/>quorum 0@localhost:9093"]
        end
        subgraph APP["Deployments (initContainers gate on TCP)"]
            K1["order-service"]
            K2["saga-orchestrator"]
            K3["payment-service"]
            K4["inventory-service"]
            K5["flaky-external-service"]
        end
        subgraph OBSK["Observability"]
            KPR["prometheus"]
            KGR["grafana"]
            KZ["zipkin"]
        end
    end

    NP["NodePort on minikube ip"]
    NP -->|":30081"| K1
    NP -->|":30300"| KGR
    NP -->|":30090"| KPR
    NP -->|":30411"| KZ

    APP --> KPG
    APP --> KKAF
    KPR --> APP
```

Deltas versus compose, all forced by Kubernetes semantics:

- **KRaft quorum uses `0@localhost:9093`, not `kafka:9093`.** A Service only
  publishes endpoints for *Ready* pods, so a self-reference through the Service
  would deadlock Kafka's own startup.
- **Heap capped** via `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` plus memory
  limits, so several JVMs fit on a laptop.
- **initContainers** block each app on Postgres/Kafka TCP reachability, the
  equivalent of compose's `depends_on: service_healthy`.
- **Images tagged `:local`** with `imagePullPolicy: IfNotPresent`, built inside
  minikube's docker daemon - no registry in the loop.

See [`k8s/README.md`](../k8s/README.md) for the deploy script and memory setup.

---

### AWS (ECS Fargate) - target deployment

A proposed production-style topology on AWS. It is a design target only; this repo
does not ship Terraform/CDK for it yet. Editable source:
[`diagrams/aws-ecs-architecture.drawio`](diagrams/aws-ecs-architecture.drawio)
(open it in [diagrams.net](https://app.diagrams.net) or the VS Code Draw.io extension).

![AWS ECS architecture](diagrams/aws-ecs-architecture.png)

| Local / k8s component | AWS service |
| --- | --- |
| 5 Spring Boot containers | Amazon ECS on AWS Fargate (one ECS service per app, images in ECR) |
| `postgres:16` (orders_db, saga_db, inventory_db) | Amazon RDS for PostgreSQL, Multi-AZ, credentials in Secrets Manager |
| Kafka KRaft | Amazon MSK (same 5 topics) |
| k8s Service DNS names | ECS Service Connect (Cloud Map), so `FLAKY_SERVICE_URL` etc. keep working |
| Zipkin | ADOT collector sidecar (Zipkin receiver) to AWS X-Ray |
| Prometheus / Grafana | ADOT remote write to Amazon Managed Prometheus, dashboards in Amazon Managed Grafana |
| `docker logs` | CloudWatch Logs (`awslogs` driver) |

Only `order-service` sits behind the public Application Load Balancer; every
other service, MSK and RDS live in private subnets.

## 11. Design boundaries

What this architecture deliberately does *not* do - see the README's
["Corners cut"](../README.md#corners-cut-given-timescope-constraints) for the
full list:

- No authn/authz, no TLS on any hop (Kafka, Postgres, HTTP all plaintext).
- No schema registry - contracts are plain JSON records kept in sync by hand.
- One Postgres instance hosting three databases, rather than one instance each.
- Single-partition topics: enough to show the patterns, not tuned for
  partition-key parallelism.
- No consumer-side dedup ledger beyond the saga's own transition guards.
