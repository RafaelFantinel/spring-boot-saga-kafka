package com.refarch.orderservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format published to the {@code order-events} Kafka topic. Services
 * consuming this (saga-orchestrator) do not share a Java type with
 * order-service - each service owns its own copy of the contract, which is
 * deliberate: it keeps services independently deployable at the cost of some
 * duplication, matching how teams typically version topic schemas in
 * practice (here informally; a schema registry would formalize this).
 */
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

    public OrderCreatedEvent(UUID orderId, String customerId, String sku, int quantity, BigDecimal amount, Instant occurredAt) {
        this(TYPE, orderId, customerId, sku, quantity, amount, occurredAt);
    }
}
