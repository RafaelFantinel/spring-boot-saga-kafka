package com.refarch.sagaorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable saga state. This row is the single source of truth for "where is
 * this order in the fulfillment process" - it is what makes the orchestrator
 * crash-recoverable: on restart we reconstruct in-flight sagas from this
 * table (see SagaRecoveryRunner) instead of relying on in-memory state, and
 * every Kafka event handler re-reads the current row by orderId before
 * applying a transition rather than trusting anything cached in the JVM.
 */
@Entity
@Table(name = "saga_instance")
public class SagaInstance {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SagaInstance() {
        // JPA
    }

    public SagaInstance(UUID id, UUID orderId, String customerId, String sku, int quantity, BigDecimal amount,
                         SagaStep currentStep, SagaStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.sku = sku;
        this.quantity = quantity;
        this.amount = amount;
        this.currentStep = currentStep;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SagaInstance start(UUID orderId, String customerId, String sku, int quantity, BigDecimal amount) {
        Instant now = Instant.now();
        return new SagaInstance(UUID.randomUUID(), orderId, customerId, sku, quantity, amount,
                SagaStep.STARTED, SagaStatus.IN_PROGRESS, now, now);
    }

    public void transitionTo(SagaStep step, SagaStatus status) {
        this.currentStep = step;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
