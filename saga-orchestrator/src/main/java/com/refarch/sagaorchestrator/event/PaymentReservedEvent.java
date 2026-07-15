package com.refarch.sagaorchestrator.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentReservedEvent(String type, UUID orderId, String transactionId, Instant occurredAt) {
    public static final String TYPE = "PaymentReserved";
}
