package com.refarch.inventoryservice.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Not emitted by the current two-step saga (only payment is compensated when
 * inventory reservation fails, since inventory is the last step), but fully
 * implemented here since inventory-service owns stock release as a first-class
 * operation - e.g. useful directly, or for a future saga step ordered after
 * inventory (shipping, etc.) that would need to release stock on its own failure.
 */
public record ReleaseInventoryCommand(String type, UUID orderId, String sku, int quantity, Instant occurredAt) {
    public static final String TYPE = "ReleaseInventory";
}
