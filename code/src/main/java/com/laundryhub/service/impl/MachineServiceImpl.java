package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import com.laundryhub.dto.request.MachineRequest;
import com.laundryhub.dto.response.MachineResponse;
import com.laundryhub.event.MachineStatusChangedEvent;
import com.laundryhub.exception.BookingConflictException;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.MachineMapper;
import com.laundryhub.repository.BranchRepository;
import com.laundryhub.repository.MachineRepository;
import com.laundryhub.repository.UsageSessionRepository;
import com.laundryhub.service.MachineService;
import com.laundryhub.service.state.MachineStateFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

@Service
@Validated
@Transactional(readOnly = true)
public class MachineServiceImpl implements MachineService {
    private final MachineRepository machines;
    private final BranchRepository branches;
    private final UsageSessionRepository sessions;
    private final MachineMapper mapper;
    private final MachineStateFactory states;
    private final ApplicationEventPublisher events;

    public MachineServiceImpl(MachineRepository machines, BranchRepository branches,
                              UsageSessionRepository sessions, MachineMapper mapper,
                              MachineStateFactory states, ApplicationEventPublisher events) {
        this.machines = machines;
        this.branches = branches;
        this.sessions = sessions;
        this.mapper = mapper;
        this.states = states;
        this.events = events;
    }

    @Override
    public Page<MachineResponse> search(Long branchId, MachineStatus status, MachineType type, Pageable pageable) {
        return machines.search(branchId, status, type, pageable).map(mapper::toResponse);
    }

    @Override
    public MachineResponse findById(Long id) {
        return mapper.toResponse(machines.findById(id).orElseThrow(() -> notFound(id)));
    }

    @Override
    @Transactional
    public MachineResponse create(MachineRequest request) {
        Branch branch = getBranch(request.branchId());
        if (machines.existsByBranch_IdAndName(request.branchId(), request.name().strip())) {
            throw duplicateName();
        }
        Machine machine = new Machine();
        mapper.apply(machine, branch, request);
        // Flush makes concurrent duplicate names fail inside this transaction (DB unique constraint).
        return mapper.toResponse(machines.saveAndFlush(machine));
    }

    @Override
    @Transactional
    public MachineResponse update(Long id, MachineRequest request) {
        Machine machine = lockedMachine(id);
        Branch branch = getBranch(request.branchId());
        if (machines.existsByBranch_IdAndNameAndIdNot(request.branchId(), request.name().strip(), id)) {
            throw duplicateName();
        }
        boolean changesIdentity = !Objects.equals(machine.getBranch().getId(), request.branchId())
                || machine.getMachineType() != request.machineType();
        if (changesIdentity && sessions.existsByMachine_Id(id)) {
            throw new BookingConflictException("Cannot move or change type of a machine with session history");
        }
        mapper.apply(machine, branch, request);
        machines.flush();
        return mapper.toResponse(machine);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Machine machine = lockedMachine(id);
        if (machine.getStatus() == MachineStatus.IN_USE || sessions.existsByMachine_Id(id)) {
            throw new BookingConflictException("Cannot delete a machine in use or with session history");
        }
        machines.delete(machine);
        machines.flush();
    }

    @Override
    @Transactional
    public MachineResponse changeStatus(Long id, MachineStatus status) {
        // IN_USE belongs to the session lifecycle; staff cannot bypass start/finish here.
        if (status != MachineStatus.AVAILABLE && status != MachineStatus.OUT_OF_SERVICE) {
            throw new BusinessRuleException("Manual status must be AVAILABLE or OUT_OF_SERVICE");
        }
        Machine machine = lockedMachine(id);
        if (!states.getState(machine.getStatus()).canSetOutOfService()) {
            throw new BookingConflictException("Cannot change status while a machine is in use");
        }
        if (machine.getStatus() != status) {
            machine.setStatus(status);
            machines.flush();
            events.publishEvent(new MachineStatusChangedEvent(id, status));
        }
        return mapper.toResponse(machine);
    }

    private Machine lockedMachine(Long id) {
        return machines.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private Branch getBranch(Long id) {
        return branches.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch " + id + " not found"));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Machine " + id + " not found");
    }

    private DuplicateResourceException duplicateName() {
        return new DuplicateResourceException("Machine name already exists in this branch");
    }
}
