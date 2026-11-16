package com.soaproject.inventory.repository;

import com.soaproject.inventory.entity.PurchaseOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, String> {
}