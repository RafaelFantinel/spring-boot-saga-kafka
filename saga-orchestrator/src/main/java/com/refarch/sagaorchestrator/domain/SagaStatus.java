package com.refarch.sagaorchestrator.domain;

public enum SagaStatus {
    IN_PROGRESS,
    COMPENSATING,
    COMPLETED,
    FAILED
}
