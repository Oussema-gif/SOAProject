package com.soaproject.inventory.repository;

import com.soaproject.inventory.entity.SparePartEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SparePartRepository extends JpaRepository<SparePartEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePartEntity p where p.partId = :partId")
    Optional<SparePartEntity> findWithLockByPartId(@Param("partId") String partId);
}