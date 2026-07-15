package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

/** Published by payment-service after it processes a ReleasePayment compensation command. */
public record PaymentReleasedEvent(String type, UUID orderId, Instant occurredAt) {
    public static final String TYPE = "PaymentReleased";
}
