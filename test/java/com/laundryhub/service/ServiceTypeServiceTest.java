package com.laundryhub.service;

import com.laundryhub.domain.entity.ServiceType;
import com.laundryhub.dto.request.ServiceTypeRequest;
import com.laundryhub.dto.response.ServiceTypeResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.ServiceTypeMapper;
import com.laundryhub.repository.ServiceTypeRepository;
import com.laundryhub.service.impl.ServiceTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    private ServiceTypeService serviceTypeService;

    @BeforeEach
    void setUp() {
        serviceTypeService = new ServiceTypeServiceImpl(serviceTypeRepository, new ServiceTypeMapper());
    }

    @Test
    void findActive_returnsOnlyActiveTypesFromRepository() {
        when(serviceTypeRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(serviceType(1L, "Wash", true)));

        List<ServiceTypeResponse> result = serviceTypeService.findActive();

        assertThat(result).extracting(ServiceTypeResponse::name).containsExactly("Wash");
    }

    @Test
    void findById_unknown_throwsNotFound() {
        when(serviceTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTypeService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_newName_savesActiveTypeWithTrimmedName() {
        when(serviceTypeRepository.existsByNameIgnoreCase("Dry clean")).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse response = serviceTypeService.create(request("  Dry clean  ", null));

        assertThat(response.name()).isEqualTo("Dry clean");
        assertThat(response.pricePerKg()).isEqualByComparingTo("50.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateNameIgnoringCase_throwsConflictAndSavesNothing() {
        when(serviceTypeRepository.existsByNameIgnoreCase("wash")).thenReturn(true);

        assertThatThrownBy(() -> serviceTypeService.create(request("wash", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    void update_changesPricesAndCanReactivate() {
        ServiceType existing = serviceType(1L, "Wash", false);
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameIgnoreCaseAndIdNot("Wash", 1L)).thenReturn(false);

        ServiceTypeResponse response = serviceTypeService.update(1L, request("Wash", true));

        assertThat(response.pricePerKg()).isEqualByComparingTo("50.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    void update_toNameOfAnotherType_throwsConflict() {
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(serviceType(1L, "Wash", true)));
        when(serviceTypeRepository.existsByNameIgnoreCaseAndIdNot("Iron only", 1L)).thenReturn(true);

        assertThatThrownBy(() -> serviceTypeService.update(1L, request("Iron only", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivate_switchesOffInsteadOfDeleting() {
        ServiceType existing = serviceType(1L, "Wash", true);
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(existing));

        serviceTypeService.deactivate(1L);

        assertThat(existing.isActive()).isFalse();
        verify(serviceTypeRepository, never()).delete(any());
    }

    private static ServiceTypeRequest request(String name, Boolean active) {
        return new ServiceTypeRequest(name, new BigDecimal("50.00"), new BigDecimal("15.00"), active);
    }

    private static ServiceType serviceType(Long id, String name, boolean active) {
        ServiceType serviceType = new ServiceType();
        serviceType.setId(id);
        serviceType.setName(name);
        serviceType.setPricePerKg(new BigDecimal("25.00"));
        serviceType.setExpressSurcharge(new BigDecimal("10.00"));
        serviceType.setActive(active);
        return serviceType;
    }
}
