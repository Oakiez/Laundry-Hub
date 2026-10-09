package com.laundryhub.service;

import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.exception.BookingConflictException;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.repository.UsageSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;

@Component
public class BookingValidator {
    private final UsageSessionRepository sessions;
    private final Clock clock;

    public BookingValidator(UsageSessionRepository sessions,
                            @Qualifier("bookingClock") Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Returns the server-calculated end time after validation.
     * The caller must check and save in one transaction, with a shared lock
     * strategy for concurrent bookings; this validator does not acquire a lock.
     */
    public LocalDateTime validate(Machine machine, LocalDateTime startTime, Integer durationMinutes) {
        if (machine == null || machine.getId() == null || machine.getStatus() == null) {
            throw new BusinessRuleException("A persisted machine with a status is required");
        }
        if (machine.getStatus() == MachineStatus.OUT_OF_SERVICE) {
            throw new BusinessRuleException("Machine is out of service");
        }
        if (startTime == null) {
            throw new BusinessRuleException("Start time is required");
        }
        if (durationMinutes == null || durationMinutes < 10 || durationMinutes > 180) {
            throw new BusinessRuleException("Duration must be between 10 and 180 minutes");
        }
        if (startTime.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Start time must not be in the past");
        }

        LocalDateTime endTime;
        try {
            endTime = startTime.plusMinutes(durationMinutes);
        } catch (DateTimeException exception) {
            throw new BusinessRuleException("Booking end time is outside the supported range");
        }
        if (sessions.existsOverlap(machine.getId(), startTime, endTime)) {
            throw new BookingConflictException("Machine already has a booking in this time range");
        }
        return endTime;
    }
}
