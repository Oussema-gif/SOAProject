package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.CreateMachineRequest;
import com.soaproject.maintenance.dto.MachineResponse;
import com.soaproject.maintenance.dto.UpdateMachineRequest;
import com.soaproject.maintenance.entity.MachineEntity;
import com.soaproject.maintenance.entity.MachineStatus;
import com.soaproject.maintenance.exception.DuplicateResourceException;
import com.soaproject.maintenance.exception.ResourceNotFoundException;
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

import java.util.ArrayList;
import java.util.List;

@Service
public class MachineService {

    private final MachineRepository repository;

    public MachineService(MachineRepository repository) {
        this.repository = repository;
    }

    public Page<MachineResponse> list(int page, int size, MachineStatus status, String q) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("machineId").ascending());
        Specification<MachineEntity> spec = filterBy(status, q);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    public MachineResponse get(String machineId) {
        return toResponse(findOrThrow(machineId));
    }

    public String getStatus(String machineId) {
        return findOrThrow(machineId).getStatus().name();
    }

    @Transactional
    public MachineResponse create(CreateMachineRequest req) {
        if (repository.existsByMachineId(req.machineId())) {
            throw new DuplicateResourceException("Machine already exists: " + req.machineId());
        }
        MachineEntity entity = new MachineEntity();
        entity.setMachineId(req.machineId());
        entity.setName(req.name());
        entity.setLocation(req.location());
        entity.setModel(req.model());
        entity.setStatus(req.status() == null ? MachineStatus.RUNNING : req.status());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public MachineResponse update(String machineId, UpdateMachineRequest req) {
        MachineEntity entity = findOrThrow(machineId);
        entity.setName(req.name());
        entity.setLocation(req.location());
        entity.setModel(req.model());
        entity.setStatus(req.status());
        return toResponse(entity);
    }

    @Transactional
    public void delete(String machineId) {
        MachineEntity entity = findOrThrow(machineId);
        try {
            repository.delete(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Cannot delete machine " + machineId + ": still referenced by alerts or work orders");
        }
    }

    private MachineEntity findOrThrow(String machineId) {
        return repository.findByMachineId(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found: " + machineId));
    }

    private MachineResponse toResponse(MachineEntity e) {
        return new MachineResponse(
                e.getMachineId(),
                e.getName(),
                e.getLocation(),
                e.getModel(),
                e.getStatus().name(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private Specification<MachineEntity> filterBy(MachineStatus status, String q) {
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("machineId")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}