package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.AssignTechnicianRequest;
import com.soaproject.maintenance.dto.CreateWorkOrderRequest;
import com.soaproject.maintenance.dto.ReservationAssociationRequest;
import com.soaproject.maintenance.dto.ReservationReferenceResponse;
import com.soaproject.maintenance.dto.WorkOrderResponse;
import com.soaproject.maintenance.entity.AlertEntity;
import com.soaproject.maintenance.entity.MachineEntity;
import com.soaproject.maintenance.entity.ReservationStatus;
import com.soaproject.maintenance.entity.TechnicianEntity;
import com.soaproject.maintenance.entity.WorkOrderEntity;
import com.soaproject.maintenance.entity.WorkOrderPriority;
import com.soaproject.maintenance.entity.WorkOrderReservationEntity;
import com.soaproject.maintenance.entity.WorkOrderStatus;
import com.soaproject.maintenance.exception.DuplicateResourceException;
import com.soaproject.maintenance.exception.InvalidStateTransitionException;
import com.soaproject.maintenance.exception.ResourceNotFoundException;
import com.soaproject.maintenance.repository.AlertRepository;
import com.soaproject.maintenance.repository.MachineRepository;
import com.soaproject.maintenance.repository.TechnicianRepository;
import com.soaproject.maintenance.repository.UserRepository;
import com.soaproject.maintenance.repository.WorkOrderRepository;
import com.soaproject.maintenance.repository.WorkOrderReservationRepository;
import com.soaproject.maintenance.util.PageRequestValidator;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkOrderService {

    private static final Set<WorkOrderStatus> TERMINAL =
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED);

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final AlertRepository alertRepository;
    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            WorkOrderReservationRepository reservationRepository,
                            MachineRepository machineRepository,
                            AlertRepository alertRepository,
                            TechnicianRepository technicianRepository,
                            UserRepository userRepository) {
        this.workOrderRepository = workOrderRepository;
        this.reservationRepository = reservationRepository;
        this.machineRepository = machineRepository;
        this.alertRepository = alertRepository;
        this.technicianRepository = technicianRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(WorkOrderStatus status,
                                        String machineCode,
                                        UUID technicianId,
                                        int page,
                                        int size) {
        PageRequestValidator.validate(page, size);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return workOrderRepository.findAll(filterBy(status, machineCode, technicianId), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public WorkOrderCreateResult create(CreateWorkOrderRequest req) {
        MachineEntity machine = machineRepository.findByMachineId(req.machineId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Machine not found: " + req.machineId()));

        AlertEntity alert = null;
        if (req.alertId() != null) {
            alert = alertRepository.findById(req.alertId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Alert not found: " + req.alertId()));
            if (!alert.getMachine().getId().equals(machine.getId())) {
                throw new InvalidStateTransitionException(
                        "Alert " + req.alertId() + " does not belong to machine " + req.machineId());
            }
            Optional<WorkOrderEntity> liveForAlert =
                    workOrderRepository.findFirstByAlert_IdAndStatusNotInOrderByCreatedAtDesc(
                            alert.getId(), TERMINAL);
            if (liveForAlert.isPresent()) {
                return new WorkOrderCreateResult(toResponse(liveForAlert.get()), false);
            }
        }

        WorkOrderEntity wo = new WorkOrderEntity();
        wo.setMachine(machine);
        wo.setAlert(alert);
        wo.setTitle(req.title());
        wo.setDescription(req.description());
        wo.setPriority(req.priority() == null ? WorkOrderPriority.MEDIUM : req.priority());
        wo.setStatus(WorkOrderStatus.OPEN);
        WorkOrderEntity saved = workOrderRepository.saveAndFlush(wo);
        return new WorkOrderCreateResult(toResponse(saved), true);
    }

    @Transactional
    public WorkOrderResponse updateStatus(UUID id, WorkOrderStatus target, Authentication authentication) {
        WorkOrderEntity wo = findOrThrow(id);
        WorkOrderStatus from = wo.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Cannot move work order from " + from + " to " + target);
        }
        if (target == WorkOrderStatus.IN_PROGRESS && wo.getTechnician() == null) {
            throw new InvalidStateTransitionException(
                    "Cannot start work order without an assigned technician");
        }
        enforceTechnicianOwnership(authentication, wo, target);

        wo.setStatus(target);
        if (target == WorkOrderStatus.COMPLETED) {
            wo.setCompletedAt(Instant.now());
        }
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse assign(UUID id, AssignTechnicianRequest req) {
        WorkOrderEntity wo = findOrThrow(id);
        if (wo.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException(
                    "Cannot assign a technician to a " + wo.getStatus() + " work order");
        }
        TechnicianEntity tech = technicianRepository.findById(req.technicianId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Technician not found: " + req.technicianId()));
        if (!tech.isActive()) {
            throw new InvalidStateTransitionException(
                    "Cannot assign an inactive technician");
        }
        wo.setTechnician(tech);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse attachReservation(UUID id, ReservationAssociationRequest req) {
        WorkOrderEntity wo = findOrThrow(id);
        ReservationStatus target = req.status() == null ? ReservationStatus.ACTIVE : req.status();

        if (target == ReservationStatus.ACTIVE) {
            return attachActive(wo, req);
        }
        return releaseReservation(wo, req);
    }

    private WorkOrderResponse attachActive(WorkOrderEntity wo, ReservationAssociationRequest req) {
        if (wo.getStatus() != WorkOrderStatus.CHECKING_INVENTORY
                && wo.getStatus() != WorkOrderStatus.PENDING_RESERVATION) {
            throw new InvalidStateTransitionException(
                    "Cannot attach a reservation while work order is " + wo.getStatus());
        }
        // Idempotent if the same reservationId is already ACTIVE on this WO+part
        Optional<WorkOrderReservationEntity> activeSamePart =
                reservationRepository.findByWorkOrder_IdAndPartIdAndStatus(
                        wo.getId(), req.partId(), ReservationStatus.ACTIVE);
        if (activeSamePart.isPresent()) {
            if (activeSamePart.get().getReservationId().equals(req.reservationId())) {
                wo.setStatus(WorkOrderStatus.READY_FOR_MAINTENANCE);
                return toResponse(wo);
            }
            throw new DuplicateResourceException(
                    "An active reservation already exists for part " + req.partId()
                            + " on this work order");
        }
        // reservationId must not already be attached to another work order
        Optional<WorkOrderReservationEntity> sameReservationId =
                reservationRepository.findByReservationId(req.reservationId());
        if (sameReservationId.isPresent()
                && !sameReservationId.get().getWorkOrder().getId().equals(wo.getId())) {
            throw new DuplicateResourceException(
                    "Reservation " + req.reservationId() + " is already attached to another work order");
        }

        WorkOrderReservationEntity r = new WorkOrderReservationEntity();
        r.setWorkOrder(wo);
        r.setReservationId(req.reservationId());
        r.setPartId(req.partId());
        r.setQuantity(req.quantity());
        r.setStatus(ReservationStatus.ACTIVE);
        reservationRepository.saveAndFlush(r);

        wo.setStatus(WorkOrderStatus.READY_FOR_MAINTENANCE);
        return toResponse(wo);
    }

    private WorkOrderResponse releaseReservation(WorkOrderEntity wo, ReservationAssociationRequest req) {
        WorkOrderReservationEntity r = reservationRepository.findByReservationId(req.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found: " + req.reservationId()));
        if (!r.getWorkOrder().getId().equals(wo.getId())) {
            throw new DuplicateResourceException(
                    "Reservation " + req.reservationId() + " does not belong to this work order");
        }
        if (r.getStatus() == ReservationStatus.ACTIVE) {
            r.setStatus(ReservationStatus.RELEASED);
            r.setReleasedAt(Instant.now());
        }
        return toResponse(wo);
    }

    private void enforceTechnicianOwnership(Authentication authentication,
                                            WorkOrderEntity wo,
                                            WorkOrderStatus target) {
        if (authentication == null) return;
        boolean hasBroadRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MAINTENANCE_OPERATOR"));
        if (hasBroadRole) return;

        boolean isTechnician = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_TECHNICIAN"));
        if (!isTechnician) {
            throw new AccessDeniedException("Insufficient permission");
        }

        if (target != WorkOrderStatus.IN_PROGRESS && target != WorkOrderStatus.COMPLETED) {
            throw new AccessDeniedException(
                    "Technicians can only move work orders to IN_PROGRESS or COMPLETED");
        }
        if (wo.getTechnician() == null || wo.getTechnician().getUserId() == null) {
            throw new AccessDeniedException("Work order is not assigned to you");
        }
        UUID currentUserId = userRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(u -> u.getId())
                .orElse(null);
        if (currentUserId == null || !currentUserId.equals(wo.getTechnician().getUserId())) {
            throw new AccessDeniedException("Work order is not assigned to you");
        }
    }

    private WorkOrderEntity findOrThrow(UUID id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found: " + id));
    }

    private WorkOrderResponse toResponse(WorkOrderEntity wo) {
        List<ReservationReferenceResponse> reservations =
                reservationRepository.findByWorkOrder_IdOrderByCreatedAtAsc(wo.getId())
                        .stream()
                        .map(r -> new ReservationReferenceResponse(
                                r.getReservationId(),
                                r.getPartId(),
                                r.getQuantity(),
                                r.getStatus().name(),
                                r.getReleasedAt()))
                        .toList();

        return new WorkOrderResponse(
                wo.getId(),
                wo.getMachine().getMachineId(),
                wo.getAlert() != null ? wo.getAlert().getId() : null,
                wo.getTechnician() != null ? wo.getTechnician().getId() : null,
                wo.getTechnician() != null ? wo.getTechnician().getFullName() : null,
                wo.getTitle(),
                wo.getDescription(),
                wo.getPriority().name(),
                wo.getStatus().name(),
                wo.getCompletedAt(),
                reservations,
                wo.getCreatedAt(),
                wo.getUpdatedAt());
    }

    private Specification<WorkOrderEntity> filterBy(WorkOrderStatus status,
                                                    String machineCode,
                                                    UUID technicianId) {
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (machineCode != null && !machineCode.isBlank()) {
                preds.add(cb.equal(root.get("machine").get("machineId"), machineCode));
            }
            if (technicianId != null) {
                preds.add(cb.equal(root.get("technician").get("id"), technicianId));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}