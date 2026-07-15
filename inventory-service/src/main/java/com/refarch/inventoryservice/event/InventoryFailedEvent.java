package com.refarch.inventoryservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryFailedEvent(String type, UUID orderId, String sku, String reason, Instant occurredAt) {
    public static final String TYPE = "InventoryFailed";

    public InventoryFailedEvent(UUID orderId, String sku, String reason, Instant occurredAt) {
        this(TYPE, orderId, sku, reason, occurredAt);
    }
}
