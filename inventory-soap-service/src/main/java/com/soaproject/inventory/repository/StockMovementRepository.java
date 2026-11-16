package com.soaproject.inventory.repository;

import com.soaproject.inventory.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
}