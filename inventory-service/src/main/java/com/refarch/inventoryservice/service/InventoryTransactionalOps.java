package com.refarch.inventoryservice.service;

import com.refarch.inventoryservice.domain.Inventory;
import com.refarch.inventoryservice.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Each method here is its own transaction (REQUIRES_NEW), so a single failed
 * attempt never poisons a wider transaction and each retry in
 * InventoryReservationService re-reads the row with a fresh {@code @Version}.
 * Deliberately a SEPARATE bean from InventoryReservationService: calling a
 * {@code @Transactional} method via {@code this.method()} (self-invocation)
 * bypasses Spring's proxy entirely and silently no-ops the annotation, so the
 * retry loop must call through a real Spring-managed collaborator bean.
 */
@Component
public class InventoryTransactionalOps {

    private static final Logger log = LoggerFactory.getLogger(InventoryTransactionalOps.class);

    private final InventoryRepository inventoryRepository;

    public InventoryTransactionalOps(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InventoryReservationService.ReservationOutcome reserveOnce(String sku, int quantity) {
        Inventory inventory = inventoryRepository.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("Unknown SKU: " + sku));

        if (!inventory.tryReserve(quantity)) {
            return InventoryReservationService.ReservationOutcome.INSUFFICIENT_STOCK;
        }
        inventoryRepository.saveAndFlush(inventory);
        log.info("Reserved {} units of {} (available now {}, reserved now {})",
                quantity, sku, inventory.getAvailableQty(), inventory.getReservedQty());
        return InventoryReservationService.ReservationOutcome.RESERVED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOnce(String sku, int quantity) {
        Inventory inventory = inventoryRepository.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("Unknown SKU: " + sku));
        inventory.release(quantity);
        inventoryRepository.saveAndFlush(inventory);
        log.info("Released {} units of {} (available now {}, reserved now {})",
                quantity, sku, inventory.getAvailableQty(), inventory.getReservedQty());
    }
}
