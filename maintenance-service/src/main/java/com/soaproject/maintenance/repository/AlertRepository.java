package com.soaproject.maintenance.repository;

import com.soaproject.maintenance.entity.AlertEntity;
import com.soaproject.maintenance.entity.AlertMetricType;
import com.soaproject.maintenance.entity.AlertStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository
        extends JpaRepository<AlertEntity, UUID>,
                JpaSpecificationExecutor<AlertEntity> {

    @Override
    @EntityGraph(attributePaths = "machine")
    Optional<AlertEntity> findById(UUID id);

    Optional<AlertEntity> findBySourceEventId(String sourceEventId);

    Optional<AlertEntity> findFirstByMachine_IdAndMetricTypeAndStatusInOrderByOccurredAtDesc(
            UUID machineUuid,
            AlertMetricType metricType,
            Collection<AlertStatus> statuses);

    List<AlertEntity> findByMachine_IdAndStatusIn(UUID machineUuid, Collection<AlertStatus> statuses);
}