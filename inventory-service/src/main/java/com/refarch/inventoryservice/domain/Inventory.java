package com.refarch.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stock ledger row for a single SKU. Concurrency safety when multiple orders
 * race to reserve the same SKU is handled with JPA optimistic locking
 * ({@code @Version}): two concurrent transactions reading the same row both
 * attempt to commit, and the second writer gets an
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException},
 * which InventoryReservationService catches and retries against a freshly
 * re-read row - no pessimistic DB locks needed for this workload.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private String sku;

    @Column(nullable = false)
    private int availableQty;

    @Column(nullable = false)
    private int reservedQty;

    @Version
    private long version;

    protected Inventory() {
        // JPA
    }

    public Inventory(String sku, int availableQty, int reservedQty) {
        this.sku = sku;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
    }

    public boolean tryReserve(int quantity) {
        if (availableQty < quantity) {
            return false;
        }
        availableQty -= quantity;
        reservedQty += quantity;
        return true;
    }

    public void release(int quantity) {
        int toRelease = Math.min(quantity, reservedQty);
        reservedQty -= toRelease;
        availableQty += toRelease;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public long getVersion() {
        return version;
    }
}
