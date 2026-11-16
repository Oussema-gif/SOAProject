package com.soaproject.maintenance.repository;

import com.soaproject.maintenance.entity.MachineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MachineRepository
        extends JpaRepository<MachineEntity, UUID>,
                JpaSpecificationExecutor<MachineEntity> {

    Optional<MachineEntity> findByMachineId(String machineId);
    boolean existsByMachineId(String machineId);
}