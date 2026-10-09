package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import com.laundryhub.dto.request.MachineRequest;
import com.laundryhub.event.MachineStatusChangedEvent;
import com.laundryhub.exception.*;
import com.laundryhub.mapper.MachineMapper;
import com.laundryhub.repository.*;
import com.laundryhub.service.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineServiceImplTest {
    @Mock MachineRepository machines;
    @Mock BranchRepository branches;
    @Mock UsageSessionRepository sessions;
    @Mock ApplicationEventPublisher events;
    private MachineServiceImpl service;
    private Branch branch;
    private Machine machine;

    @BeforeEach
    void setUp() {
        service = new MachineServiceImpl(machines, branches, sessions, new MachineMapper(),
                new MachineStateFactory(List.of(new AvailableState(), new ReservedState(),
                        new InUseState(), new OutOfServiceState())), events);
        branch = new Branch();
        branch.setId(2L);
        branch.setName("Branch A");
        machine = new Machine();
        ReflectionTestUtils.setField(machine, "id", 1L);
        machine.setBranch(branch);
        machine.setName("Washer A");
        machine.setMachineType(MachineType.WASHER);
    }

    private MachineRequest request(Long branchId, MachineType type) {
        return new MachineRequest(branchId, " Washer A ", type,
                new BigDecimal("20.00"), new BigDecimal("1.50"));
    }

    @Test
    void createsAvailableMachineWithNormalizedNameAndPrices() {
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(machines.saveAndFlush(any())).thenAnswer(invocation -> {
            Machine saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
        var result = service.create(request(2L, MachineType.WASHER));
        assertEquals(9L, result.id());
        assertEquals(2L, result.branchId());
        assertEquals("Washer A", result.name());
        assertEquals(MachineStatus.AVAILABLE, result.status());
        assertEquals(new BigDecimal("1.50"), result.pricePerMinute());
        verify(machines).existsByBranch_IdAndName(2L, "Washer A");
    }

    @Test
    void duplicateNameDoesNotSave() {
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(machines.existsByBranch_IdAndName(2L, "Washer A")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(request(2L, MachineType.WASHER)));
        verify(machines, never()).saveAndFlush(any());
    }

    @Test
    void missingBranchDoesNotSave() {
        assertThrows(ResourceNotFoundException.class, () -> service.create(request(99L, MachineType.WASHER)));
        verifyNoInteractions(machines);
    }

    @Test
    void searchPreservesFiltersPaginationAndSorting() {
        var pageable = PageRequest.of(0, 5, Sort.by("name"));
        when(machines.search(2L, MachineStatus.AVAILABLE, MachineType.WASHER, pageable))
                .thenReturn(new PageImpl<>(List.of(machine), pageable, 8));
        var page = service.search(2L, MachineStatus.AVAILABLE, MachineType.WASHER, pageable);
        assertEquals(8, page.getTotalElements());
        assertEquals(pageable.getSort(), page.getSort());
        assertEquals(1L, page.getContent().get(0).id());
    }

    @Test
    void missingMachineReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
    }

    @Test
    void updatesPricesWithoutChangingStatus() {
        machine.setStatus(MachineStatus.OUT_OF_SERVICE);
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        var result = service.update(1L, request(2L, MachineType.WASHER));
        assertEquals(new BigDecimal("20.00"), result.basePrice());
        assertEquals(MachineStatus.OUT_OF_SERVICE, result.status());
        verify(machines).existsByBranch_IdAndNameAndIdNot(2L, "Washer A", 1L);
        verify(machines).flush();
        verifyNoInteractions(events);
    }

    @Test
    void updateRejectsDuplicateWithoutModifyingMachine() {
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(machines.existsByBranch_IdAndNameAndIdNot(2L, "Washer A", 1L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.update(1L, request(2L, MachineType.WASHER)));
        assertEquals(BigDecimal.ZERO, machine.getBasePrice());
        verify(machines, never()).flush();
    }

    @Test
    void cannotChangeMachineTypeAfterItHasSessions() {
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(sessions.existsByMachine_Id(1L)).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.update(1L, request(2L, MachineType.DRYER)));
        assertEquals(MachineType.WASHER, machine.getMachineType());
    }

    @Test
    void cannotMoveMachineWithSessionHistory() {
        Branch other = new Branch();
        other.setId(3L);
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        when(branches.findById(3L)).thenReturn(Optional.of(other));
        when(sessions.existsByMachine_Id(1L)).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.update(1L, request(3L, MachineType.WASHER)));
        assertSame(branch, machine.getBranch());
    }

    @Test
    void deletesUnusedMachine() {
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        service.delete(1L);
        verify(machines).delete(machine);
        verify(machines).flush();
    }

    @Test
    void preservesMachineWithSessionHistory() {
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        when(sessions.existsByMachine_Id(1L)).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.delete(1L));
        verify(machines, never()).delete(any());
    }

    @Test
    void cannotDeleteMachineInUse() {
        machine.setStatus(MachineStatus.IN_USE);
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        assertThrows(BookingConflictException.class, () -> service.delete(1L));
        verify(machines, never()).delete(any());
    }

    @Test
    void publishesExactlyOneEventForActualStatusChange() {
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        var result = service.changeStatus(1L, MachineStatus.OUT_OF_SERVICE);
        assertEquals(MachineStatus.OUT_OF_SERVICE, result.status());
        service.changeStatus(1L, MachineStatus.OUT_OF_SERVICE);
        verify(events, times(1)).publishEvent(new MachineStatusChangedEvent(1L, MachineStatus.OUT_OF_SERVICE));
        verify(machines, times(1)).flush();
    }

    @Test
    void repairsOutOfServiceMachine() {
        machine.setStatus(MachineStatus.OUT_OF_SERVICE);
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        assertEquals(MachineStatus.AVAILABLE, service.changeStatus(1L, MachineStatus.AVAILABLE).status());
        verify(events).publishEvent(new MachineStatusChangedEvent(1L, MachineStatus.AVAILABLE));
    }

    @ParameterizedTest
    @EnumSource(value = MachineStatus.class, names = {"AVAILABLE", "OUT_OF_SERVICE"})
    void cannotBypassSessionLifecycleWhileMachineIsInUse(MachineStatus target) {
        machine.setStatus(MachineStatus.IN_USE);
        when(machines.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
        assertThrows(BookingConflictException.class, () -> service.changeStatus(1L, target));
        assertEquals(MachineStatus.IN_USE, machine.getStatus());
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(value = MachineStatus.class, names = {"RESERVED", "IN_USE"})
    void manualStatusCannotStartOrReserveMachine(MachineStatus target) {
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(1L, target));
        verifyNoInteractions(machines, events);
    }
}
