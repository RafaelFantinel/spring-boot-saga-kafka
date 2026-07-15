package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryFailedEvent(String type, UUID orderId, String sku, String reason, Instant occurredAt) {
    public static final String TYPE = "InventoryFailed";
}
