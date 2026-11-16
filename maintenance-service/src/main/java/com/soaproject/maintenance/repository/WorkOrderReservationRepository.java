package com.soaproject.maintenance.repository;

import com.soaproject.maintenance.entity.ReservationStatus;
import com.soaproject.maintenance.entity.WorkOrderReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderReservationRepository
        extends JpaRepository<WorkOrderReservationEntity, UUID> {

    List<WorkOrderReservationEntity> findByWorkOrder_IdOrderByCreatedAtAsc(UUID workOrderId);

    Optional<WorkOrderReservationEntity> findByWorkOrder_IdAndPartIdAndStatus(
            UUID workOrderId, String partId, ReservationStatus status);

    Optional<WorkOrderReservationEntity> findByReservationId(String reservationId);
}