package com.refarch.sagaorchestrator.repository;

import com.refarch.sagaorchestrator.domain.SagaInstance;
import com.refarch.sagaorchestrator.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByOrderId(UUID orderId);

    List<SagaInstance> findByStatusIn(List<SagaStatus> statuses);
}
