package com.refarch.paymentservice.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentReservedEvent(String type, UUID orderId, String transactionId, Instant occurredAt) {
    public static final String TYPE = "PaymentReserved";

    public PaymentReservedEvent(UUID orderId, String transactionId, Instant occurredAt) {
        this(TYPE, orderId, transactionId, occurredAt);
    }
}
