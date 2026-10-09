package com.laundryhub.mapper;

import com.laundryhub.domain.entity.Notification;
import com.laundryhub.dto.response.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
