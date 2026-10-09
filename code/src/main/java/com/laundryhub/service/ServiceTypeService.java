package com.laundryhub.service;

import com.laundryhub.dto.request.ServiceTypeRequest;
import com.laundryhub.dto.response.ServiceTypeResponse;

import java.util.List;

public interface ServiceTypeService {

    /** Service types customers can order right now (active only), sorted by name. */
    List<ServiceTypeResponse> findActive();

    ServiceTypeResponse findById(Long id);

    ServiceTypeResponse create(ServiceTypeRequest request);

    ServiceTypeResponse update(Long id, ServiceTypeRequest request);

    /** Soft delete: old orders still reference the row, so it is switched off instead of removed. */
    void deactivate(Long id);
}
