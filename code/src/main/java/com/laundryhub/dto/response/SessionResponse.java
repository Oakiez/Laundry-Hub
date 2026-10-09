package com.laundryhub.dto.response;
import com.laundryhub.domain.enums.SessionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record SessionResponse(Long id, Long machineId, Long userId, SessionStatus status,
        LocalDateTime startTime, LocalDateTime endTime, Integer durationMinutes,
        BigDecimal amount, LocalDateTime createdAt) {
}
