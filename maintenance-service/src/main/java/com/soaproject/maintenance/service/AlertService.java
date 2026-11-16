package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.AlertResponse;
import com.soaproject.maintenance.dto.CreateAlertRequest;
import com.soaproject.maintenance.entity.AlertEntity;
import com.soaproject.maintenance.entity.AlertMetricType;
import com.soaproject.maintenance.entity.AlertSeverity;
import com.soaproject.maintenance.entity.AlertStatus;
import com.soaproject.maintenance.entity.MachineEntity;
import com.soaproject.maintenance.exception.InvalidStateTransitionException;
import com.soaproject.maintenance.exception.ResourceNotFoundException;
import com.soaproject.maintenance.repository.AlertRepository;
import com.soaproject.maintenance.repository.MachineRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertService {

    private static final List<AlertStatus> OPEN_STATUSES =
            List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);

    private final AlertRepository alertRepository;
    private final MachineRepository machineRepository;

    public AlertService(AlertRepository alertRepository, MachineRepository machineRepository) {
        this.alertRepository = alertRepository;
        this.machineRepository = machineRepository;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> list(AlertStatus status,
                                    String machineId,
                                    AlertSeverity severity,
                                    int page,
                                    int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return alertRepository.findAll(filterBy(status, machineId, severity), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AlertResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AlertCreateResult create(CreateAlertRequest req) {
        MachineEntity machine = machineRepository.findByMachineId(req.machineId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Machine not found: " + req.machineId()));

        // 1. Idempotency by sourceEventId
        if (req.sourceEventId() != null && !req.sourceEventId().isBlank()) {
            Optional<AlertEntity> existing = alertRepository.findBySourceEventId(req.sourceEventId());
            if (existing.isPresent()) {
                return new AlertCreateResult(toResponse(existing.get()), false);
            }
        }

        // 2. Dedup by machine + metric while an equivalent open alert exists
        Optional<AlertEntity> openEquivalent =
                alertRepository.findFirstByMachine_IdAndMetricTypeAndStatusInOrderByOccurredAtDesc(
                        machine.getId(), req.metricType(), OPEN_STATUSES);
        if (openEquivalent.isPresent()) {
            return new AlertCreateResult(toResponse(openEquivalent.get()), false);
        }

        // 3. Insert new
        AlertEntity entity = new AlertEntity();
        entity.setMachine(machine);
        entity.setMetricType(req.metricType());
        entity.setSeverity(req.severity());
        entity.setObservedValue(req.observedValue());
        entity.setThresholdValue(req.thresholdValue());
        entity.setMessage(req.message());
        entity.setOccurredAt(req.occurredAt());
        entity.setSourceEventId(blankToNull(req.sourceEventId()));
        entity.setStatus(AlertStatus.ACTIVE);

        try {
            AlertEntity saved = alertRepository.saveAndFlush(entity);
            return new AlertCreateResult(toResponse(saved), true);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request won the race — resolve to that one.
            if (req.sourceEventId() != null) {
                Optional<AlertEntity> byEvent = alertRepository.findBySourceEventId(req.sourceEventId());
                if (byEvent.isPresent()) return new AlertCreateResult(toResponse(byEvent.get()), false);
            }
            Optional<AlertEntity> byMetric =
                    alertRepository.findFirstByMachine_IdAndMetricTypeAndStatusInOrderByOccurredAtDesc(
                            machine.getId(), req.metricType(), OPEN_STATUSES);
            if (byMetric.isPresent()) return new AlertCreateResult(toResponse(byMetric.get()), false);
            throw ex;
        }
    }

    @Transactional
    public AlertResponse acknowledge(UUID id) {
        AlertEntity alert = findOrThrow(id);
        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new InvalidStateTransitionException(
                    "Cannot acknowledge alert in status " + alert.getStatus());
        }
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        return toResponse(alert);
    }

    @Transactional
    public AlertResponse resolve(UUID id) {
        AlertEntity alert = findOrThrow(id);
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new InvalidStateTransitionException("Alert already resolved");
        }
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        return toResponse(alert);
    }

    private AlertEntity findOrThrow(UUID id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
    }

    private AlertResponse toResponse(AlertEntity e) {
        return new AlertResponse(
                e.getId(),
                e.getMachine().getMachineId(),
                e.getMetricType().name(),
                e.getSeverity().name(),
                e.getObservedValue(),
                e.getThresholdValue(),
                e.getMessage(),
                e.getStatus().name(),
                e.getSourceEventId(),
                e.getOccurredAt(),
                e.getAcknowledgedAt(),
                e.getResolvedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private Specification<AlertEntity> filterBy(AlertStatus status,
                                                String machineCode,
                                                AlertSeverity severity) {
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (machineCode != null && !machineCode.isBlank()) {
                preds.add(cb.equal(root.get("machine").get("machineId"), machineCode));
            }
            if (severity != null) {
                preds.add(cb.equal(root.get("severity"), severity));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}