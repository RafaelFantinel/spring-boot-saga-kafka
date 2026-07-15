package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

/** Published by saga-orchestrator to order-events once every step succeeds. */
public record OrderCompletedEvent(String type, UUID orderId, Instant occurredAt) {
    public static final String TYPE = "OrderCompleted";

    public OrderCompletedEvent(UUID orderId, Instant occurredAt) {
        this(TYPE, orderId, occurredAt);
    }
}
