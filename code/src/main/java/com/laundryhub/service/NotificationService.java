package com.laundryhub.service;

import com.laundryhub.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /** สร้างแจ้งเตือนใหม่ให้ผู้ใช้ (ยังไม่อ่าน) */
    Notification notifyUser(Long userId, String message);

    /** @param unread true = เฉพาะที่ยังไม่อ่าน, false = เฉพาะที่อ่านแล้ว, null = ทั้งหมด */
    Page<Notification> findByUser(Long userId, Boolean unread, Pageable pageable);

    /** @throws com.laundryhub.exception.ResourceNotFoundException ถ้าไม่พบ */
    Notification markRead(Long notificationId);

    Notification getById(Long notificationId);
}
