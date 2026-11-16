package com.soaproject.inventory.repository;

import com.soaproject.inventory.entity.PartReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartReservationRepository extends JpaRepository<PartReservationEntity, String> {
    Optional<PartReservationEntity> findByRequestId(String requestId);
}