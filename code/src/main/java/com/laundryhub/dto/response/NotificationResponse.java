package com.laundryhub.dto.response;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        String message,
        boolean read,
        LocalDateTime createdAt) {
}
