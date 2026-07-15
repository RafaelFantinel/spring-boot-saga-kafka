package com.refarch.sagaorchestrator.recovery;

import com.refarch.sagaorchestrator.domain.SagaInstance;
import com.refarch.sagaorchestrator.domain.SagaStatus;
import com.refarch.sagaorchestrator.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs once on startup and reconstructs in-flight sagas from Postgres rather
 * than relying on any in-memory registry (there isn't one). Because every
 * SagaOrchestratorService handler looks up SagaInstance by orderId directly
 * from the database on every event, correctness after a crash/restart falls
 * out naturally from the state being durable - this runner exists to make
 * that recovery observable (logged) and to flag sagas that may need manual
 * attention (e.g. stuck mid-compensation for an unusually long time), which
 * is the kind of operational visibility you'd want before trusting an
 * orchestrator in production.
 */
@Component
public class SagaRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryRunner.class);

    private final SagaInstanceRepository sagaInstanceRepository;

    public SagaRecoveryRunner(SagaInstanceRepository sagaInstanceRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SagaInstance> inFlight = sagaInstanceRepository.findByStatusIn(
                List.of(SagaStatus.IN_PROGRESS, SagaStatus.COMPENSATING));

        if (inFlight.isEmpty()) {
            log.info("Saga recovery: no in-flight sagas found, starting clean");
            return;
        }

        log.warn("Saga recovery: found {} in-flight saga(s) from a previous run - they will resume " +
                "as their next expected Kafka event arrives, since state is persisted in saga_instance", inFlight.size());
        for (SagaInstance saga : inFlight) {
            log.warn("  recovered saga {} order={} step={} status={} updatedAt={}",
                    saga.getId(), saga.getOrderId(), saga.getCurrentStep(), saga.getStatus(), saga.getUpdatedAt());
        }
    }
}
