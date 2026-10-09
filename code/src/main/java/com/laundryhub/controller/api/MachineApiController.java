package com.laundryhub.controller.api;

import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import com.laundryhub.dto.request.ChangeMachineStatusRequest;
import com.laundryhub.dto.request.MachineRequest;
import com.laundryhub.dto.response.MachineResponse;
import com.laundryhub.dto.response.PageResponse;
import com.laundryhub.service.MachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/machines")
@Tag(name = "Machines")
public class MachineApiController {
    private final MachineService machines;

    public MachineApiController(MachineService machines) {
        this.machines = machines;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search machines, paged and sorted (authenticated users)")
    public PageResponse<MachineResponse> search(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) MachineStatus status,
            @RequestParam(required = false) MachineType type,
            @ParameterObject @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return PageResponse.from(machines.search(branchId, status, type, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a machine (authenticated users)")
    public MachineResponse findById(@PathVariable Long id) {
        return machines.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a machine (admin)")
    public MachineResponse create(@Valid @RequestBody MachineRequest request) {
        return machines.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a machine (admin)")
    public MachineResponse update(@PathVariable Long id, @Valid @RequestBody MachineRequest request) {
        return machines.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a machine without usage history (admin)")
    public void delete(@PathVariable Long id) {
        machines.delete(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Set AVAILABLE or OUT_OF_SERVICE (staff/admin)")
    public MachineResponse changeStatus(@PathVariable Long id,
            @Valid @RequestBody ChangeMachineStatusRequest request) {
        return machines.changeStatus(id, request.status());
    }
}
