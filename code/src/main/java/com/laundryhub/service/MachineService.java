package com.laundryhub.service;

import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import com.laundryhub.dto.request.MachineRequest;
import com.laundryhub.dto.response.MachineResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MachineService {
    Page<MachineResponse> search(Long branchId, MachineStatus status, MachineType type, Pageable pageable);
    MachineResponse findById(Long id);
    MachineResponse create(@NotNull @Valid MachineRequest request);
    MachineResponse update(Long id, @NotNull @Valid MachineRequest request);
    void delete(Long id);
    MachineResponse changeStatus(Long id, @NotNull MachineStatus status);
}
