package com.refarch.sagaorchestrator.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservePaymentCommand(String type, UUID orderId, BigDecimal amount, Instant occurredAt) {
    public static final String TYPE = "ReservePayment";

    public ReservePaymentCommand(UUID orderId, BigDecimal amount, Instant occurredAt) {
        this(TYPE, orderId, amount, occurredAt);
    }
}
