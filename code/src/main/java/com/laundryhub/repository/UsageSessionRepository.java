package com.laundryhub.repository;

import com.laundryhub.domain.entity.UsageSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

    // Strict inequalities allow adjacent bookings: [10:00, 11:00), [11:00, 12:00).
    // A transaction/lock in the booking service is still needed for concurrent requests.
    @Query("""
            select (count(s) > 0) from UsageSession s
            where s.machine.id = :machineId
              and s.status in (
                  com.laundryhub.domain.enums.SessionStatus.RESERVED,
                  com.laundryhub.domain.enums.SessionStatus.IN_USE)
              and s.startTime < :end
              and s.endTime > :start
            """)
    boolean existsOverlap(@Param("machineId") Long machineId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    Page<UsageSession> findByMachine_Id(Long machineId, Pageable pageable);

    Page<UsageSession> findByUserId(Long userId, Pageable pageable);
}
