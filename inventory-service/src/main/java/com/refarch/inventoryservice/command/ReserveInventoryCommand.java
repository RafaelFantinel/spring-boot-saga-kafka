package com.refarch.inventoryservice.command;

import java.time.Instant;
import java.util.UUID;

public record ReserveInventoryCommand(String type, UUID orderId, String sku, int quantity, Instant occurredAt) {
    public static final String TYPE = "ReserveInventory";
}
