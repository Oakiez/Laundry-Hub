package com.laundryhub.dto.request;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
public record BookSessionRequest(
        @NotNull @Positive Long userId,
        @NotNull LocalDateTime startTime,
        @NotNull @Min(10) @Max(180) Integer durationMinutes) {
}
