package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(String type, UUID orderId, String reason, Instant occurredAt) {
    public static final String TYPE = "PaymentFailed";
}
