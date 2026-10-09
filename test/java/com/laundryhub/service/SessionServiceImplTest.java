package com.laundryhub.service;

import com.laundryhub.domain.entity.*;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.event.*;
import com.laundryhub.exception.*;
import com.laundryhub.mapper.SessionMapper;
import com.laundryhub.repository.*;
import com.laundryhub.service.impl.SessionServiceImpl;
import com.laundryhub.service.pricing.SelfServicePricing;
import com.laundryhub.service.state.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {
    @Mock UsageSessionRepository sessions;
    @Mock MachineRepository machines;
    @Mock UserRepository users;
    @Mock ApplicationEventPublisher events;
    SessionServiceImpl service;
    Machine machine;
    User user;
    UsageSession session;
    final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 10, 0);

    @BeforeEach
    void setup() {
        service = new SessionServiceImpl(sessions, machines, users,
                new BookingValidator(sessions, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)),
                new SelfServicePricing(),
                new MachineStateFactory(List.of(new AvailableState(), new ReservedState(), new InUseState(), new OutOfServiceState())),
                new SessionMapper(), events);
        user = new User();
        user.setId(3L);
        user.setRole(Role.CUSTOMER);
        machine = new Machine();
        ReflectionTestUtils.setField(machine, "id", 5L);
        machine.setBasePrice(new BigDecimal("20"));
        machine.setPricePerMinute(new BigDecimal("1.50"));
        session = new UsageSession();
        ReflectionTestUtils.setField(session, "id", 7L);
        session.setMachine(machine);
        session.setUser(user);
        session.setAmount(new BigDecimal("65"));
    }

    void prepareBook() {
        when(users.findById(3L)).thenReturn(Optional.of(user));
        when(machines.findByIdForUpdate(5L)).thenReturn(Optional.of(machine));
    }

    void prepareTransition() {
        when(sessions.findMachineIdBySessionId(7L)).thenReturn(Optional.of(5L));
        when(machines.findByIdForUpdate(5L)).thenReturn(Optional.of(machine));
        when(sessions.findByIdForUpdate(7L)).thenReturn(Optional.of(session));
    }

    BookSessionRequest request() { return new BookSessionRequest(3L, now.plusHours(1), 30); }

    @Test void booksWithServerPriceAndEndTimeWithoutReservingMachine() {
        prepareBook();
        when(sessions.saveAndFlush(any())).thenAnswer(inv -> {
            UsageSession saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        var response = service.book(5L, 3L, false, request());
        assertEquals(0, new BigDecimal("65").compareTo(response.amount()));
        assertEquals(now.plusHours(1).plusMinutes(30), response.endTime());
        assertEquals(SessionStatus.RESERVED, response.status());
        assertEquals(MachineStatus.AVAILABLE, machine.getStatus());
        var order = inOrder(machines, sessions, events);
        order.verify(machines).findByIdForUpdate(5L);
        order.verify(sessions).existsOverlap(5L, request().startTime(), response.endTime());
        order.verify(sessions).saveAndFlush(any());
        order.verify(events).publishEvent(new SessionStatusChangedEvent(7L, 3L, SessionStatus.RESERVED));
    }

    @Test void refusesBookingForAnotherCustomerBeforeDatabaseWrite() {
        assertThrows(AccessDeniedException.class, () -> service.book(5L, 9L, false, request()));
        verifyNoInteractions(users, machines, sessions, events);
    }

    @Test void staffCanBookForCustomer() {
        prepareBook();
        assertEquals(3L, service.book(5L, 9L, true, request()).userId());
    }

    @Test void disabledCustomerCannotBook() {
        user.setEnabled(false);
        when(users.findById(3L)).thenReturn(Optional.of(user));
        assertThrows(BusinessRuleException.class, () -> service.book(5L, 3L, false, request()));
        verifyNoInteractions(machines, sessions, events);
    }

    @Test void staffAccountCannotBeBookingOwner() {
        user.setRole(Role.STAFF);
        when(users.findById(3L)).thenReturn(Optional.of(user));
        assertThrows(BusinessRuleException.class, () -> service.book(5L, 9L, true, request()));
    }

    @Test void overlapPreventsSaveAndEvent() {
        prepareBook();
        when(sessions.existsOverlap(anyLong(), any(), any())).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.book(5L, 3L, false, request()));
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test void startsThenFinishesAndPublishesBothStatuses() {
        prepareTransition();
        assertEquals(SessionStatus.IN_USE, service.start(7L, 3L, false).status());
        assertEquals(MachineStatus.IN_USE, machine.getStatus());
        assertEquals(SessionStatus.COMPLETED, service.finish(7L, 9L, true).status());
        assertEquals(MachineStatus.AVAILABLE, machine.getStatus());
        verify(events).publishEvent(new SessionStatusChangedEvent(7L, 3L, SessionStatus.IN_USE));
        verify(events).publishEvent(new SessionStatusChangedEvent(7L, 3L, SessionStatus.COMPLETED));
        verify(events).publishEvent(new MachineStatusChangedEvent(5L, MachineStatus.IN_USE));
        verify(events).publishEvent(new MachineStatusChangedEvent(5L, MachineStatus.AVAILABLE));
        var order = inOrder(sessions, machines);
        order.verify(sessions).findMachineIdBySessionId(7L);
        order.verify(machines).findByIdForUpdate(5L);
        order.verify(sessions).findByIdForUpdate(7L);
    }

    @Test void cancellationLeavesRunningMachineAlone() {
        prepareTransition();
        machine.setStatus(MachineStatus.IN_USE);
        assertEquals(SessionStatus.CANCELLED, service.cancel(7L, 3L, false).status());
        assertEquals(MachineStatus.IN_USE, machine.getStatus());
        verify(events).publishEvent(new SessionStatusChangedEvent(7L, 3L, SessionStatus.CANCELLED));
        verify(events, never()).publishEvent(isA(MachineStatusChangedEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"IN_USE", "COMPLETED", "CANCELLED"})
    void cannotStartOrCancelUnlessReserved(SessionStatus status) {
        prepareTransition();
        session.setStatus(status);
        assertThrows(BusinessRuleException.class, () -> service.start(7L, 3L, false));
        assertThrows(BusinessRuleException.class, () -> service.cancel(7L, 3L, false));
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(value = MachineStatus.class, names = {"RESERVED", "IN_USE", "OUT_OF_SERVICE"})
    void unavailableMachineCannotStart(MachineStatus status) {
        prepareTransition();
        machine.setStatus(status);
        assertThrows(BusinessRuleException.class, () -> service.start(7L, 3L, false));
        assertEquals(SessionStatus.RESERVED, session.getStatus());
        verifyNoInteractions(events);
    }

    @Test void cannotFinishReservation() {
        prepareTransition();
        assertThrows(BusinessRuleException.class, () -> service.finish(7L, 3L, false));
        verifyNoInteractions(events);
    }

    @Test void inconsistentMachineStateCannotFinish() {
        prepareTransition();
        session.setStatus(SessionStatus.IN_USE);
        assertThrows(BusinessRuleException.class, () -> service.finish(7L, 3L, false));
        assertEquals(SessionStatus.IN_USE, session.getStatus());
    }

    @Test void nonOwnerCannotChangeAnyStatus() {
        prepareTransition();
        assertThrows(AccessDeniedException.class, () -> service.start(7L, 9L, false));
        assertThrows(AccessDeniedException.class, () -> service.finish(7L, 9L, false));
        assertThrows(AccessDeniedException.class, () -> service.cancel(7L, 9L, false));
        verify(sessions, never()).flush();
        verifyNoInteractions(events);
    }

    @Test void missingSessionDoesNotAcquireMachineLock() {
        assertThrows(ResourceNotFoundException.class, () -> service.start(99L, 3L, false));
        verifyNoInteractions(machines, events);
    }

    @Test void ownerReadsMappedPageAndInvalidSortFailsBeforeQuery() {
        when(users.existsById(3L)).thenReturn(true);
        Pageable page = PageRequest.of(0, 10, Sort.by("startTime"));
        when(sessions.findByUser_Id(3L, page)).thenReturn(new PageImpl<>(List.of(session), page, 1));
        assertEquals(7L, service.findForUser(3L, 3L, false, page).getContent().get(0).id());
        assertThrows(BusinessRuleException.class, () -> service.findForUser(3L, 3L, false,
                PageRequest.of(0, 10, Sort.by("user.password"))));
    }

    @Test void nonOwnerCannotReadSessionOrHistoryAndCustomerCannotReadMachineHistory() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session));
        assertThrows(AccessDeniedException.class, () -> service.findById(7L, 9L, false));
        assertThrows(AccessDeniedException.class, () -> service.findForUser(3L, 9L, false, Pageable.unpaged()));
        assertThrows(AccessDeniedException.class, () -> service.findForMachine(5L, false, Pageable.unpaged()));
    }
}
