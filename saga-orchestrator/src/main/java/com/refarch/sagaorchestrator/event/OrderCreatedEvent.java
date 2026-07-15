package com.refarch.sagaorchestrator.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        String type,
        UUID orderId,
        String customerId,
        String sku,
        int quantity,
        BigDecimal amount,
        Instant occurredAt
) {
    public static final String TYPE = "OrderCreated";
}
