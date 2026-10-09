package com.laundryhub.mapper;

import com.laundryhub.domain.entity.ServiceType;
import com.laundryhub.dto.request.ServiceTypeRequest;
import com.laundryhub.dto.response.ServiceTypeResponse;
import org.springframework.stereotype.Component;

@Component
public class ServiceTypeMapper {

    public ServiceTypeResponse toResponse(ServiceType serviceType) {
        return new ServiceTypeResponse(serviceType.getId(), serviceType.getName(), serviceType.getPricePerKg(),
                serviceType.getExpressSurcharge(), serviceType.isActive());
    }

    public ServiceType toEntity(ServiceTypeRequest request) {
        ServiceType serviceType = new ServiceType();
        apply(serviceType, request);
        return serviceType;
    }

    public void apply(ServiceType serviceType, ServiceTypeRequest request) {
        serviceType.setName(request.name().trim());
        serviceType.setPricePerKg(request.pricePerKg());
        serviceType.setExpressSurcharge(request.expressSurcharge());
        if (request.active() != null) {
            serviceType.setActive(request.active());
        }
    }
}
