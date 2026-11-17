package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.CreateTechnicianRequest;
import com.soaproject.maintenance.dto.TechnicianResponse;
import com.soaproject.maintenance.dto.UpdateTechnicianRequest;
import com.soaproject.maintenance.entity.TechnicianEntity;
import com.soaproject.maintenance.exception.DuplicateResourceException;
import com.soaproject.maintenance.exception.ResourceNotFoundException;
import com.soaproject.maintenance.repository.TechnicianRepository;
import com.soaproject.maintenance.repository.UserRepository;
import com.soaproject.maintenance.util.PageRequestValidator;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TechnicianService {

    private final TechnicianRepository repository;
    private final UserRepository userRepository;

    public TechnicianService(TechnicianRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public Page<TechnicianResponse> list(int page, int size, Boolean active, String q) {
        PageRequestValidator.validate(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        Specification<TechnicianEntity> spec = filterBy(active, q);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    public TechnicianResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TechnicianResponse create(CreateTechnicianRequest req) {
        if (repository.existsByEmailIgnoreCase(req.email())) {
            throw new DuplicateResourceException("Technician email already exists: " + req.email());
        }
        if (req.userId() != null) {
            validateUserLink(req.userId());
        }
        TechnicianEntity entity = new TechnicianEntity();
        entity.setFullName(req.fullName());
        entity.setEmail(req.email());
        entity.setUserId(req.userId());
        entity.setActive(req.active() == null ? true : req.active());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public TechnicianResponse update(UUID id, UpdateTechnicianRequest req) {
        TechnicianEntity entity = findOrThrow(id);
        if (!entity.getEmail().equalsIgnoreCase(req.email())
                && repository.existsByEmailIgnoreCase(req.email())) {
            throw new DuplicateResourceException("Technician email already exists: " + req.email());
        }
        if (req.userId() != null
                && !req.userId().equals(entity.getUserId())) {
            validateUserLink(req.userId());
        }
        entity.setFullName(req.fullName());
        entity.setEmail(req.email());
        entity.setUserId(req.userId());
        entity.setActive(req.active());
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id) {
        TechnicianEntity entity = findOrThrow(id);
        repository.delete(entity);
    }

    private void validateUserLink(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        if (repository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User already linked to a technician: " + userId);
        }
    }

    private TechnicianEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + id));
    }

    private TechnicianResponse toResponse(TechnicianEntity e) {
        return new TechnicianResponse(
                e.getId(),
                e.getFullName(),
                e.getEmail(),
                e.getUserId(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private Specification<TechnicianEntity> filterBy(Boolean active, String q) {
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (active != null) {
                preds.add(cb.equal(root.get("active"), active));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}