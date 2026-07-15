package com.refarch.inventoryservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Public entry point for stock operations: retries on optimistic-lock
 * conflicts by delegating each attempt to {@link InventoryTransactionalOps},
 * a separate Spring bean so the per-attempt {@code @Transactional} boundary
 * is actually honored (see that class's javadoc on self-invocation).
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final InventoryTransactionalOps transactionalOps;

    public InventoryReservationService(InventoryTransactionalOps transactionalOps) {
        this.transactionalOps = transactionalOps;
    }

    public enum ReservationOutcome {
        RESERVED,
        INSUFFICIENT_STOCK
    }

    public ReservationOutcome reserve(String sku, int quantity) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionalOps.reserveOnce(sku, quantity);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict reserving sku={} qty={}, attempt {}/{}, retrying", sku, quantity, attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Exhausted retries reserving inventory for sku=" + sku + " after " + MAX_ATTEMPTS + " attempts");
    }

    public void release(String sku, int quantity) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transactionalOps.releaseOnce(sku, quantity);
                return;
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict releasing sku={} qty={}, attempt {}/{}, retrying", sku, quantity, attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Exhausted retries releasing inventory for sku=" + sku + " after " + MAX_ATTEMPTS + " attempts");
    }
}
