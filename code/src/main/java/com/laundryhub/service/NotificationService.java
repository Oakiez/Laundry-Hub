package com.laundryhub.service;

import com.laundryhub.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /** สร้างแจ้งเตือนใหม่ให้ผู้ใช้ (ยังไม่อ่าน) */
    Notification notifyUser(Long userId, String message);

    /** @param unread true = เฉพาะที่ยังไม่อ่าน, false = เฉพาะที่อ่านแล้ว, null = ทั้งหมด */
    Page<Notification> findByUser(Long userId, Boolean unread, Pageable pageable);

    /**
     * ทำเครื่องหมายว่าอ่านแล้ว เฉพาะเจ้าของแจ้งเตือน
     *
     * @throws com.laundryhub.exception.ResourceNotFoundException ถ้าไม่พบ
     * @throws org.springframework.security.access.AccessDeniedException ถ้าไม่ใช่เจ้าของ
     */
    Notification markRead(Long notificationId, Long requesterUserId);

    Notification getById(Long notificationId);
}
