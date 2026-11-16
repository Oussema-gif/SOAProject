package com.soaproject.maintenance.repository;

import com.soaproject.maintenance.entity.WorkOrderEntity;
import com.soaproject.maintenance.entity.WorkOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository
        extends JpaRepository<WorkOrderEntity, UUID>,
                JpaSpecificationExecutor<WorkOrderEntity> {

    @Override
    @EntityGraph(attributePaths = {"machine", "alert", "technician"})
    Optional<WorkOrderEntity> findById(UUID id);

    Optional<WorkOrderEntity> findFirstByAlert_IdAndStatusNotInOrderByCreatedAtDesc(
            UUID alertId, Collection<WorkOrderStatus> terminalStatuses);
}