package com.refarch.paymentservice.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(String type, UUID orderId, String reason, Instant occurredAt) {
    public static final String TYPE = "PaymentFailed";

    public PaymentFailedEvent(UUID orderId, String reason, Instant occurredAt) {
        this(TYPE, orderId, reason, occurredAt);
    }
}
