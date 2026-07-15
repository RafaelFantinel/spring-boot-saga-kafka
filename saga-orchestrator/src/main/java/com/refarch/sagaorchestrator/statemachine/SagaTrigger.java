package com.refarch.sagaorchestrator.statemachine;

/**
 * Triggers that can move the saga from one {@code SagaStep} to another.
 * These correspond 1:1 to the domain events the orchestrator consumes from
 * {@code order-events}, {@code payment-events} and {@code inventory-events}.
 */
public enum SagaTrigger {
    ORDER_CREATED,
    PAYMENT_RESERVED,
    PAYMENT_FAILED,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PAYMENT_RELEASED
}
