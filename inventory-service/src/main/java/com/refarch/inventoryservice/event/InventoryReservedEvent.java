package com.refarch.inventoryservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(String type, UUID orderId, String sku, Instant occurredAt) {
    public static final String TYPE = "InventoryReserved";

    public InventoryReservedEvent(UUID orderId, String sku, Instant occurredAt) {
        this(TYPE, orderId, sku, occurredAt);
    }
}
