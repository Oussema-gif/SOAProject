package com.soaproject.maintenance.repository;

import com.soaproject.maintenance.entity.TechnicianEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TechnicianRepository
        extends JpaRepository<TechnicianEntity, UUID>,
                JpaSpecificationExecutor<TechnicianEntity> {

    Optional<TechnicianEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUserId(UUID userId);
}