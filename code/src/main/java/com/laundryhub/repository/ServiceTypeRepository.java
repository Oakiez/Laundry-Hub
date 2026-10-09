package com.laundryhub.repository;

import com.laundryhub.domain.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    boolean existsByNameIgnoreCase(String name);

    // same name check on update, ignoring the row being updated
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<ServiceType> findByActiveTrueOrderByNameAsc();
}
