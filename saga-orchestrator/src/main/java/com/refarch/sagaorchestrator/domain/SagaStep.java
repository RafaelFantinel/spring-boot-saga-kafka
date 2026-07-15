package com.refarch.sagaorchestrator.domain;

/**
 * Explicit states of the order-fulfillment saga state machine. Transitions
 * between these states (and which side effect each transition triggers) are
 * defined in a table in {@link com.refarch.sagaorchestrator.statemachine.SagaStateMachine},
 * not scattered across if/else branches.
 */
public enum SagaStep {
    STARTED,
    PAYMENT_REQUESTED,
    INVENTORY_REQUESTED,
    COMPENSATING_PAYMENT,
    COMPLETED,
    FAILED
}
