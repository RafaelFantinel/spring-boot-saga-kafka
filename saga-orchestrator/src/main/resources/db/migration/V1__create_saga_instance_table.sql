CREATE TABLE saga_instance (
    id              UUID PRIMARY KEY,
    order_id        UUID            NOT NULL UNIQUE,
    customer_id     VARCHAR(64)     NOT NULL,
    sku             VARCHAR(64)     NOT NULL,
    quantity        INT             NOT NULL,
    amount          NUMERIC(12, 2)  NOT NULL,
    current_step    VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    failure_reason  VARCHAR(256),
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_saga_instance_status ON saga_instance (status);
