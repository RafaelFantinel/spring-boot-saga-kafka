CREATE TABLE orders (
    id           UUID PRIMARY KEY,
    customer_id  VARCHAR(64)     NOT NULL,
    sku          VARCHAR(64)     NOT NULL,
    quantity     INT             NOT NULL,
    amount       NUMERIC(12, 2)  NOT NULL,
    status       VARCHAR(16)     NOT NULL,
    created_at   TIMESTAMPTZ     NOT NULL,
    updated_at   TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_orders_status ON orders (status);
