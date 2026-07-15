package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

/** Published by saga-orchestrator to order-events when the saga cannot complete, after any compensation runs. */
public record OrderFailedEvent(String type, UUID orderId, String reason, Instant occurredAt) {
    public static final String TYPE = "OrderFailed";

    public OrderFailedEvent(UUID orderId, String reason, Instant occurredAt) {
        this(TYPE, orderId, reason, occurredAt);
    }
}
