package com.refarch.sagaorchestrator.statemachine;

import com.refarch.sagaorchestrator.domain.SagaStep;

import java.util.Map;

/**
 * Explicit state machine for the order-fulfillment saga: a fixed transition
 * table mapping (current step, trigger) -> next step. This is the mechanism
 * requested instead of ad-hoc if/else chains scattered through listener
 * classes - all valid transitions are declared here in one place, and an
 * attempt to apply a trigger that isn't valid for the current step raises
 * {@link IllegalStateException} rather than silently doing nothing.
 *
 * <pre>
 * STARTED --ORDER_CREATED--&gt; PAYMENT_REQUESTED           (send ReservePayment)
 * PAYMENT_REQUESTED --PAYMENT_RESERVED--&gt; INVENTORY_REQUESTED  (send ReserveInventory)
 * PAYMENT_REQUESTED --PAYMENT_FAILED--&gt; FAILED                (publish OrderFailed)
 * INVENTORY_REQUESTED --INVENTORY_RESERVED--&gt; COMPLETED        (publish OrderCompleted)
 * INVENTORY_REQUESTED --INVENTORY_FAILED--&gt; COMPENSATING_PAYMENT (send ReleasePayment)
 * COMPENSATING_PAYMENT --PAYMENT_RELEASED--&gt; FAILED            (publish OrderFailed)
 * </pre>
 */
public final class SagaStateMachine {

    private record TransitionKey(SagaStep step, SagaTrigger trigger) {
    }

    private static final Map<TransitionKey, SagaStep> TRANSITIONS = Map.ofEntries(
            Map.entry(new TransitionKey(SagaStep.STARTED, SagaTrigger.ORDER_CREATED), SagaStep.PAYMENT_REQUESTED),
            Map.entry(new TransitionKey(SagaStep.PAYMENT_REQUESTED, SagaTrigger.PAYMENT_RESERVED), SagaStep.INVENTORY_REQUESTED),
            Map.entry(new TransitionKey(SagaStep.PAYMENT_REQUESTED, SagaTrigger.PAYMENT_FAILED), SagaStep.FAILED),
            Map.entry(new TransitionKey(SagaStep.INVENTORY_REQUESTED, SagaTrigger.INVENTORY_RESERVED), SagaStep.COMPLETED),
            Map.entry(new TransitionKey(SagaStep.INVENTORY_REQUESTED, SagaTrigger.INVENTORY_FAILED), SagaStep.COMPENSATING_PAYMENT),
            Map.entry(new TransitionKey(SagaStep.COMPENSATING_PAYMENT, SagaTrigger.PAYMENT_RELEASED), SagaStep.FAILED)
    );

    private SagaStateMachine() {
    }

    /**
     * Returns the next step for the given (current step, trigger) pair, or
     * throws if the transition is not allowed - e.g. receiving a duplicate
     * PaymentReserved for a saga that already moved past PAYMENT_REQUESTED
     * (which callers should catch and treat as an idempotent no-op / log
     * warning rather than a fatal error, since Kafka delivery is at-least-once).
     */
    public static SagaStep nextStep(SagaStep current, SagaTrigger trigger) {
        TransitionKey key = new TransitionKey(current, trigger);
        SagaStep next = TRANSITIONS.get(key);
        if (next == null) {
            throw new IllegalStateException("No transition defined for step=" + current + " trigger=" + trigger);
        }
        return next;
    }

    public static boolean canTransition(SagaStep current, SagaTrigger trigger) {
        return TRANSITIONS.containsKey(new TransitionKey(current, trigger));
    }

    public static boolean isTerminal(SagaStep step) {
        return step == SagaStep.COMPLETED || step == SagaStep.FAILED;
    }
}
