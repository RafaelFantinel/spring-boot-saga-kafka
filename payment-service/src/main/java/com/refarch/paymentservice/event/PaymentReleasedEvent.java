package com.refarch.paymentservice.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentReleasedEvent(String type, UUID orderId, Instant occurredAt) {
    public static final String TYPE = "PaymentReleased";

    public PaymentReleasedEvent(UUID orderId, Instant occurredAt) {
        this(TYPE, orderId, occurredAt);
    }
}
