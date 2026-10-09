package com.laundryhub.controller.api;

import com.laundryhub.dto.request.ServiceTypeRequest;
import com.laundryhub.dto.response.ServiceTypeResponse;
import com.laundryhub.service.ServiceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reads are open to any logged-in user (customers need the price list); writes are ADMIN only. */
@RestController
@RequestMapping("/api/v1/service-types")
@Tag(name = "Service Types")
public class ServiceTypeApiController {

    private final ServiceTypeService serviceTypeService;

    public ServiceTypeApiController(ServiceTypeService serviceTypeService) {
        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping
    @Operation(summary = "List active service types and their prices")
    public List<ServiceTypeResponse> findActive() {
        return serviceTypeService.findActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a service type")
    public ServiceTypeResponse findById(@PathVariable Long id) {
        return serviceTypeService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a service type (admin)")
    public ServiceTypeResponse create(@Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update name, prices or active flag of a service type (admin)")
    public ServiceTypeResponse update(@PathVariable Long id, @Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a service type (admin); existing orders keep their reference")
    public void deactivate(@PathVariable Long id) {
        serviceTypeService.deactivate(id);
    }
}
