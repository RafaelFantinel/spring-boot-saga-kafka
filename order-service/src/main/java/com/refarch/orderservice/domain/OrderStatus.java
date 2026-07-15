package com.refarch.orderservice.domain;

/**
 * Lifecycle of an order as seen by order-service itself. The authoritative
 * fulfillment outcome (completed/failed) is driven asynchronously by the
 * saga-orchestrator publishing OrderCompleted/OrderFailed back onto
 * {@code order-events}; order-service does not participate in the saga logic.
 */
public enum OrderStatus {
    CREATED,
    COMPLETED,
    FAILED
}
