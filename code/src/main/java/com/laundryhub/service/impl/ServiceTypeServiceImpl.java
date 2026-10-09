package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.ServiceType;
import com.laundryhub.dto.request.ServiceTypeRequest;
import com.laundryhub.dto.response.ServiceTypeResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.ServiceTypeMapper;
import com.laundryhub.repository.ServiceTypeRepository;
import com.laundryhub.service.ServiceTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ServiceTypeServiceImpl implements ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceTypeMapper serviceTypeMapper;

    public ServiceTypeServiceImpl(ServiceTypeRepository serviceTypeRepository, ServiceTypeMapper serviceTypeMapper) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceTypeMapper = serviceTypeMapper;
    }

    @Override
    public List<ServiceTypeResponse> findActive() {
        return serviceTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(serviceTypeMapper::toResponse)
                .toList();
    }

    @Override
    public ServiceTypeResponse findById(Long id) {
        return serviceTypeMapper.toResponse(getServiceType(id));
    }

    @Override
    @Transactional
    public ServiceTypeResponse create(ServiceTypeRequest request) {
        if (serviceTypeRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("Service type '" + request.name().trim() + "' already exists");
        }
        return serviceTypeMapper.toResponse(serviceTypeRepository.save(serviceTypeMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public ServiceTypeResponse update(Long id, ServiceTypeRequest request) {
        ServiceType serviceType = getServiceType(id);
        if (serviceTypeRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new DuplicateResourceException("Service type '" + request.name().trim() + "' already exists");
        }
        // the entity is managed, so the change is written when the transaction commits
        serviceTypeMapper.apply(serviceType, request);
        return serviceTypeMapper.toResponse(serviceType);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        getServiceType(id).setActive(false);
    }

    private ServiceType getServiceType(Long id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type " + id + " not found"));
    }
}
