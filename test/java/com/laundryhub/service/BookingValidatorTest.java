package com.laundryhub.service;

import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.exception.BookingConflictException;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.repository.UsageSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingValidatorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 10, 0);
    private final UsageSessionRepository sessions = mock(UsageSessionRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(),
            ZoneId.of("Asia/Bangkok"));
    private final BookingValidator validator = new BookingValidator(sessions, clock);
    private Machine machine;

    @BeforeEach
    void setUp() {
        machine = new Machine();
        ReflectionTestUtils.setField(machine, "id", 1L);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 60, 180})
    void acceptsValidDurationAndCalculatesEndTime(int minutes) {
        LocalDateTime start = NOW.plusHours(1);
        assertEquals(start.plusMinutes(minutes), validator.validate(machine, start, minutes));
        verify(sessions).existsOverlap(1L, start, start.plusMinutes(minutes));
    }

    @Test
    void acceptsStartExactlyAtCurrentTime() {
        assertEquals(NOW.plusMinutes(10), validator.validate(machine, NOW, 10));
    }

    @Test
    void rejectsStartInPastWithoutQueryingDatabase() {
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(machine, NOW.minusSeconds(1), 60));
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsOutOfServiceMachineWithoutQueryingDatabase() {
        machine.setStatus(MachineStatus.OUT_OF_SERVICE);
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(machine, NOW.plusHours(1), 60));
        verifyNoInteractions(sessions);
    }

    @ParameterizedTest
    @EnumSource(value = MachineStatus.class, names = {"AVAILABLE", "RESERVED", "IN_USE"})
    void permitsFutureBookingWhenMachineHasNoOverlappingSession(MachineStatus status) {
        // Current machine use does not prevent booking a different future slot.
        machine.setStatus(status);
        assertDoesNotThrow(() -> validator.validate(machine, NOW.plusDays(1), 60));
    }

    @Test
    void reportsBookingConflictWhenRepositoryFindsOverlap() {
        LocalDateTime start = NOW.plusHours(1);
        when(sessions.existsOverlap(1L, start, start.plusMinutes(60))).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> validator.validate(machine, start, 60));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 9, 181})
    void rejectsInvalidDurationWithoutQueryingDatabase(Integer minutes) {
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(machine, NOW.plusHours(1), minutes));
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsMissingStartTime() {
        assertThrows(BusinessRuleException.class, () -> validator.validate(machine, null, 60));
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsMissingOrUnpersistedMachine() {
        assertThrows(BusinessRuleException.class, () -> validator.validate(null, NOW, 60));
        assertThrows(BusinessRuleException.class, () -> validator.validate(new Machine(), NOW, 60));
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsMissingMachineStatus() {
        machine.setStatus(null);
        assertThrows(BusinessRuleException.class, () -> validator.validate(machine, NOW, 60));
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsEndTimeOverflow() {
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(machine, LocalDateTime.MAX, 180));
        verifyNoInteractions(sessions);
    }
}
