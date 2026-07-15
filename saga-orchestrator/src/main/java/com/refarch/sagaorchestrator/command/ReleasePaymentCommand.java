package com.refarch.sagaorchestrator.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Compensating command sent when a later saga step (inventory reservation) fails. */
public record ReleasePaymentCommand(String type, UUID orderId, BigDecimal amount, Instant occurredAt) {
    public static final String TYPE = "ReleasePayment";

    public ReleasePaymentCommand(UUID orderId, BigDecimal amount, Instant occurredAt) {
        this(TYPE, orderId, amount, occurredAt);
    }
}
