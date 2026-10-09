package com.laundryhub.repository;

import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    boolean existsByBranchIdAndName(Long branchId, String name);

    boolean existsByBranchIdAndNameAndIdNot(Long branchId, String name, Long id);

    @Query("""
            select m from Machine m
            where (:branchId is null or m.branchId = :branchId)
              and (:status is null or m.status = :status)
              and (:type is null or m.machineType = :type)
            """)
    Page<Machine> search(@Param("branchId") Long branchId,
                         @Param("status") MachineStatus status,
                         @Param("type") MachineType type,
                         Pageable pageable);
}
