package com.refarch.sagaorchestrator.orchestration;

import com.refarch.sagaorchestrator.command.ReleasePaymentCommand;
import com.refarch.sagaorchestrator.command.ReservePaymentCommand;
import com.refarch.sagaorchestrator.command.ReserveInventoryCommand;
import com.refarch.sagaorchestrator.domain.SagaInstance;
import com.refarch.sagaorchestrator.domain.SagaStatus;
import com.refarch.sagaorchestrator.domain.SagaStep;
import com.refarch.sagaorchestrator.event.OrderCompletedEvent;
import com.refarch.sagaorchestrator.event.OrderFailedEvent;
import com.refarch.sagaorchestrator.publisher.MessagePublisher;
import com.refarch.sagaorchestrator.repository.SagaInstanceRepository;
import com.refarch.sagaorchestrator.statemachine.SagaStateMachine;
import com.refarch.sagaorchestrator.statemachine.SagaTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The saga orchestrator itself: for every inbound domain event it looks up
 * the durable SagaInstance, asks the state machine what the next step is,
 * persists that transition, and performs the side effect associated with it
 * (send a command, or publish the terminal order outcome). Because every
 * handler reloads state from Postgres rather than an in-memory map, the
 * orchestrator is safe to restart mid-saga - see SagaRecoveryRunner.
 */
@Service
public class SagaOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorService.class);

    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String PAYMENT_COMMANDS_TOPIC = "payment-commands";
    private static final String INVENTORY_COMMANDS_TOPIC = "inventory-commands";

    private final SagaInstanceRepository sagaInstanceRepository;
    private final MessagePublisher messagePublisher;

    public SagaOrchestratorService(SagaInstanceRepository sagaInstanceRepository, MessagePublisher messagePublisher) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public void startSaga(UUID orderId, String customerId, String sku, int quantity, BigDecimal amount) {
        if (sagaInstanceRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Saga for order {} already exists, ignoring duplicate OrderCreated", orderId);
            return;
        }

        SagaInstance saga = SagaInstance.start(orderId, customerId, sku, quantity, amount);
        SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.ORDER_CREATED);
        saga.transitionTo(next, SagaStatus.IN_PROGRESS);
        sagaInstanceRepository.save(saga);

        messagePublisher.publish(PAYMENT_COMMANDS_TOPIC, orderId.toString(),
                new ReservePaymentCommand(orderId, amount, Instant.now()));

        log.info("Saga {} started for order {}, requested payment reservation", saga.getId(), orderId);
    }

    @Transactional
    public void onPaymentReserved(UUID orderId) {
        withSaga(orderId, SagaTrigger.PAYMENT_RESERVED, saga -> {
            SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.PAYMENT_RESERVED);
            saga.transitionTo(next, SagaStatus.IN_PROGRESS);
            sagaInstanceRepository.save(saga);

            messagePublisher.publish(INVENTORY_COMMANDS_TOPIC, orderId.toString(),
                    new ReserveInventoryCommand(orderId, saga.getSku(), saga.getQuantity(), Instant.now()));
            log.info("Saga {}: payment reserved for order {}, requested inventory reservation", saga.getId(), orderId);
        });
    }

    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        withSaga(orderId, SagaTrigger.PAYMENT_FAILED, saga -> {
            SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.PAYMENT_FAILED);
            saga.transitionTo(next, SagaStatus.FAILED);
            saga.fail(reason);
            sagaInstanceRepository.save(saga);

            messagePublisher.publish(ORDER_EVENTS_TOPIC, orderId.toString(),
                    new OrderFailedEvent(orderId, reason, Instant.now()));
            log.info("Saga {}: payment failed for order {} ({}), no compensation needed, order failed", saga.getId(), orderId, reason);
        });
    }

    @Transactional
    public void onInventoryReserved(UUID orderId) {
        withSaga(orderId, SagaTrigger.INVENTORY_RESERVED, saga -> {
            SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.INVENTORY_RESERVED);
            saga.transitionTo(next, SagaStatus.COMPLETED);
            sagaInstanceRepository.save(saga);

            messagePublisher.publish(ORDER_EVENTS_TOPIC, orderId.toString(),
                    new OrderCompletedEvent(orderId, Instant.now()));
            log.info("Saga {}: inventory reserved for order {}, saga completed successfully", saga.getId(), orderId);
        });
    }

    @Transactional
    public void onInventoryFailed(UUID orderId, String reason) {
        withSaga(orderId, SagaTrigger.INVENTORY_FAILED, saga -> {
            SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.INVENTORY_FAILED);
            saga.transitionTo(next, SagaStatus.COMPENSATING);
            saga.fail(reason);
            sagaInstanceRepository.save(saga);

            messagePublisher.publish(PAYMENT_COMMANDS_TOPIC, orderId.toString(),
                    new ReleasePaymentCommand(orderId, saga.getAmount(), Instant.now()));
            log.info("Saga {}: inventory reservation failed for order {} ({}), compensating payment", saga.getId(), orderId, reason);
        });
    }

    @Transactional
    public void onPaymentReleased(UUID orderId) {
        withSaga(orderId, SagaTrigger.PAYMENT_RELEASED, saga -> {
            SagaStep next = SagaStateMachine.nextStep(saga.getCurrentStep(), SagaTrigger.PAYMENT_RELEASED);
            saga.transitionTo(next, SagaStatus.FAILED);
            sagaInstanceRepository.save(saga);

            messagePublisher.publish(ORDER_EVENTS_TOPIC, orderId.toString(),
                    new OrderFailedEvent(orderId, saga.getFailureReason(), Instant.now()));
            log.info("Saga {}: payment compensation complete for order {}, order failed", saga.getId(), orderId);
        });
    }

    private void withSaga(UUID orderId, SagaTrigger trigger, java.util.function.Consumer<SagaInstance> action) {
        SagaInstance saga = sagaInstanceRepository.findByOrderId(orderId).orElse(null);
        if (saga == null) {
            log.warn("No saga instance found for order {} handling trigger {}, ignoring (possibly duplicate/out-of-order delivery)", orderId, trigger);
            return;
        }
        if (!SagaStateMachine.canTransition(saga.getCurrentStep(), trigger)) {
            log.warn("Saga {} for order {} cannot apply trigger {} from step {} (likely a duplicate Kafka delivery), ignoring",
                    saga.getId(), orderId, trigger, saga.getCurrentStep());
            return;
        }
        action.accept(saga);
    }
}
