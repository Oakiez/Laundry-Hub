package com.laundryhub.repository;

import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    // All future booking/lifecycle writers must acquire this same machine lock first.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Machine m where m.id = :id")
    Optional<Machine> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBranch_IdAndName(Long branchId, String name);

    boolean existsByBranch_IdAndNameAndIdNot(Long branchId, String name, Long id);

    @Query("""
            select m from Machine m
            where (:branchId is null or m.branch.id = :branchId)
              and (:status is null or m.status = :status)
              and (:type is null or m.machineType = :type)
            """)
    Page<Machine> search(@Param("branchId") Long branchId,
                         @Param("status") MachineStatus status,
                         @Param("type") MachineType type,
                         Pageable pageable);
}
