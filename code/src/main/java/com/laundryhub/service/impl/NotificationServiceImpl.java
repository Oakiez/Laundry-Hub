package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Notification;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.repository.NotificationRepository;
import com.laundryhub.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    /** ตรงกับ notifications.message VARCHAR(255) */
    private static final int MAX_MESSAGE_LENGTH = 255;

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification notifyUser(Long userId, String message) {
        String safeMessage = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH)
                : message;
        return notificationRepository.save(new Notification(userId, safeMessage));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> findByUser(Long userId, Boolean unread, Pageable pageable) {
        if (unread == null) {
            return notificationRepository.findByUserId(userId, pageable);
        }
        // unread=true หมายถึง is_read = false
        return notificationRepository.findByUserIdAndRead(userId, !unread, pageable);
    }

    @Override
    public Notification markRead(Long notificationId, Long requesterUserId) {
        Notification notification = getById(notificationId);
        if (!Objects.equals(notification.getUserId(), requesterUserId)) {
            throw new AccessDeniedException("You can only mark your own notifications as read");
        }
        notification.markRead();
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Notification getById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + notificationId + " not found"));
    }
}
