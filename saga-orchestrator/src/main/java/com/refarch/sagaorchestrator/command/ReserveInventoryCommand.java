package com.refarch.sagaorchestrator.command;

import java.time.Instant;
import java.util.UUID;

public record ReserveInventoryCommand(String type, UUID orderId, String sku, int quantity, Instant occurredAt) {
    public static final String TYPE = "ReserveInventory";

    public ReserveInventoryCommand(UUID orderId, String sku, int quantity, Instant occurredAt) {
        this(TYPE, orderId, sku, quantity, occurredAt);
    }
}
