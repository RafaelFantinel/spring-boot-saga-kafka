CREATE TABLE inventory (
    sku            VARCHAR(64) PRIMARY KEY,
    available_qty  INT         NOT NULL,
    reserved_qty    INT        NOT NULL DEFAULT 0,
    version        BIGINT      NOT NULL DEFAULT 0
);
