package com.refarch.inventoryservice.repository;

import com.refarch.inventoryservice.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, String> {
}
