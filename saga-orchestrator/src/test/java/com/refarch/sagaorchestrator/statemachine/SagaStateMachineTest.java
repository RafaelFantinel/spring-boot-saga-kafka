package com.refarch.sagaorchestrator.statemachine;

import com.refarch.sagaorchestrator.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaStateMachineTest {

    @Test
    void happyPath_progressesThroughAllStepsToCompleted() {
        SagaStep step = SagaStep.STARTED;

        step = SagaStateMachine.nextStep(step, SagaTrigger.ORDER_CREATED);
        assertThat(step).isEqualTo(SagaStep.PAYMENT_REQUESTED);

        step = SagaStateMachine.nextStep(step, SagaTrigger.PAYMENT_RESERVED);
        assertThat(step).isEqualTo(SagaStep.INVENTORY_REQUESTED);

        step = SagaStateMachine.nextStep(step, SagaTrigger.INVENTORY_RESERVED);
        assertThat(step).isEqualTo(SagaStep.COMPLETED);

        assertThat(SagaStateMachine.isTerminal(step)).isTrue();
    }

    @Test
    void paymentFailure_goesStraightToFailed_noCompensationNeeded() {
        SagaStep step = SagaStateMachine.nextStep(SagaStep.STARTED, SagaTrigger.ORDER_CREATED);
        step = SagaStateMachine.nextStep(step, SagaTrigger.PAYMENT_FAILED);
        assertThat(step).isEqualTo(SagaStep.FAILED);
    }

    @Test
    void inventoryFailure_triggersPaymentCompensation_thenFailed() {
        SagaStep step = SagaStateMachine.nextStep(SagaStep.STARTED, SagaTrigger.ORDER_CREATED);
        step = SagaStateMachine.nextStep(step, SagaTrigger.PAYMENT_RESERVED);
        step = SagaStateMachine.nextStep(step, SagaTrigger.INVENTORY_FAILED);
        assertThat(step).isEqualTo(SagaStep.COMPENSATING_PAYMENT);

        step = SagaStateMachine.nextStep(step, SagaTrigger.PAYMENT_RELEASED);
        assertThat(step).isEqualTo(SagaStep.FAILED);
    }

    @Test
    void invalidTransition_throws() {
        assertThatThrownBy(() -> SagaStateMachine.nextStep(SagaStep.STARTED, SagaTrigger.PAYMENT_RESERVED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateEventAfterTerminal_isNotTransitionable() {
        assertThat(SagaStateMachine.canTransition(SagaStep.COMPLETED, SagaTrigger.INVENTORY_RESERVED)).isFalse();
        assertThat(SagaStateMachine.canTransition(SagaStep.FAILED, SagaTrigger.PAYMENT_FAILED)).isFalse();
    }
}
