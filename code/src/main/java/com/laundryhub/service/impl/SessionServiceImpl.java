package com.laundryhub.service.impl;

import com.laundryhub.common.PageableValidator;
import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.*;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.response.SessionResponse;
import com.laundryhub.event.*;
import com.laundryhub.exception.*;
import com.laundryhub.mapper.SessionMapper;
import com.laundryhub.repository.*;
import com.laundryhub.service.BookingValidator;
import com.laundryhub.service.SessionService;
import com.laundryhub.service.pricing.PricingStrategy;
import com.laundryhub.service.pricing.SelfServicePricingInput;
import com.laundryhub.service.state.MachineStateFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.Set;

@Service
@Validated
@Transactional(readOnly = true)
public class SessionServiceImpl implements SessionService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "startTime", "endTime", "status", "amount", "createdAt");
    private final UsageSessionRepository sessions;
    private final MachineRepository machines;
    private final UserRepository users;
    private final BookingValidator validator;
    private final PricingStrategy<SelfServicePricingInput> pricing;
    private final MachineStateFactory states;
    private final SessionMapper mapper;
    private final ApplicationEventPublisher events;

    public SessionServiceImpl(UsageSessionRepository sessions, MachineRepository machines, UserRepository users,
            BookingValidator validator, PricingStrategy<SelfServicePricingInput> pricing,
            MachineStateFactory states, SessionMapper mapper, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.machines = machines;
        this.users = users;
        this.validator = validator;
        this.pricing = pricing;
        this.states = states;
        this.mapper = mapper;
        this.events = events;
    }

    @Override
    @Transactional
    public SessionResponse book(Long machineId, Long currentUserId, boolean staff, BookSessionRequest request) {
        if (request == null || request.userId() == null) throw new BusinessRuleException("Customer is required");
        requireOwner(request.userId(), currentUserId, staff);
        User user = users.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User " + request.userId() + " not found"));
        if (!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new BusinessRuleException("Booking requires an enabled customer");
        }
        // Hold this lock across overlap validation, insert and transaction commit.
        Machine machine = lockMachine(machineId);
        var endTime = validator.validate(machine, request.startTime(), request.durationMinutes());
        var amount = pricing.calculate(new SelfServicePricingInput(machine.getBasePrice(),
                machine.getPricePerMinute(), request.durationMinutes()));
        UsageSession session = new UsageSession();
        session.setMachine(machine);
        session.setUser(user);
        session.setStartTime(request.startTime());
        session.setEndTime(endTime);
        session.setDurationMinutes(request.durationMinutes());
        session.setAmount(amount);
        sessions.saveAndFlush(session);
        publishSession(session);
        return mapper.toResponse(session);
    }

    @Override
    @Transactional
    public SessionResponse start(Long sessionId, Long currentUserId, boolean staff) {
        UsageSession session = lockSession(sessionId);
        requireOwner(session.getOwnerUserId(), currentUserId, staff);
        requireStatus(session, SessionStatus.RESERVED);
        Machine machine = session.getMachine();
        if (!states.getState(machine.getStatus()).canStart()) {
            throw new BusinessRuleException("Machine cannot start in its current state");
        }
        session.setStatus(SessionStatus.IN_USE);
        machine.setStatus(MachineStatus.IN_USE);
        return flushAndPublish(session, true);
    }

    @Override
    @Transactional
    public SessionResponse finish(Long sessionId, Long currentUserId, boolean staff) {
        UsageSession session = lockSession(sessionId);
        requireOwner(session.getOwnerUserId(), currentUserId, staff);
        requireStatus(session, SessionStatus.IN_USE);
        Machine machine = session.getMachine();
        if (!states.getState(machine.getStatus()).canFinish()) {
            throw new BusinessRuleException("Machine cannot finish in its current state");
        }
        session.setStatus(SessionStatus.COMPLETED);
        machine.setStatus(MachineStatus.AVAILABLE);
        return flushAndPublish(session, true);
    }

    @Override
    @Transactional
    public SessionResponse cancel(Long sessionId, Long currentUserId, boolean staff) {
        UsageSession session = lockSession(sessionId);
        requireOwner(session.getOwnerUserId(), currentUserId, staff);
        requireStatus(session, SessionStatus.RESERVED);
        // Cancelling a future reservation must not release another running session's machine.
        session.setStatus(SessionStatus.CANCELLED);
        return flushAndPublish(session, false);
    }

    @Override
    public SessionResponse findById(Long sessionId, Long currentUserId, boolean staff) {
        UsageSession session = findSession(sessionId);
        requireOwner(session.getOwnerUserId(), currentUserId, staff);
        return mapper.toResponse(session);
    }

    @Override
    public Page<SessionResponse> findForUser(Long userId, Long currentUserId, boolean staff, Pageable pageable) {
        requireOwner(userId, currentUserId, staff);
        PageableValidator.requireSortableBy(pageable, SORT_FIELDS);
        if (!users.existsById(userId)) throw new ResourceNotFoundException("User " + userId + " not found");
        return sessions.findByUser_Id(userId, pageable).map(mapper::toResponse);
    }

    @Override
    public Page<SessionResponse> findForMachine(Long machineId, boolean staff, Pageable pageable) {
        if (!staff) throw new AccessDeniedException("Staff access is required");
        PageableValidator.requireSortableBy(pageable, SORT_FIELDS);
        if (!machines.existsById(machineId)) throw new ResourceNotFoundException("Machine " + machineId + " not found");
        return sessions.findByMachine_Id(machineId, pageable).map(mapper::toResponse);
    }

    @Override
    public Payable findPayable(Long sessionId) {
        // CheckoutFacade performs authorization in its surrounding payment transaction.
        return findSession(sessionId);
    }

    private UsageSession findSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Session " + id + " not found"));
    }

    private Machine lockMachine(Long id) {
        return machines.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Machine " + id + " not found"));
    }

    private UsageSession lockSession(Long id) {
        Long machineId = sessions.findMachineIdBySessionId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + id + " not found"));
        lockMachine(machineId);
        return sessions.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + id + " not found"));
    }

    private void requireOwner(Long ownerId, Long currentUserId, boolean staff) {
        if (currentUserId == null || ownerId == null || (!staff && !ownerId.equals(currentUserId))) {
            throw new AccessDeniedException("Session belongs to another customer");
        }
    }

    private void requireStatus(UsageSession session, SessionStatus expected) {
        if (session.getStatus() != expected) throw new BusinessRuleException("Session must be " + expected);
    }

    private SessionResponse flushAndPublish(UsageSession session, boolean machineChanged) {
        sessions.flush();
        if (machineChanged) events.publishEvent(new MachineStatusChangedEvent(session.getMachine().getId(), session.getMachine().getStatus()));
        publishSession(session);
        return mapper.toResponse(session);
    }

    private void publishSession(UsageSession session) {
        events.publishEvent(new SessionStatusChangedEvent(session.getId(), session.getOwnerUserId(), session.getStatus()));
    }
}