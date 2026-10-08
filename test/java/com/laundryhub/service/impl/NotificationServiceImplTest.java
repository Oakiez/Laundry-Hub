package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Notification;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private final Pageable pageable = PageRequest.of(0, 10);

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl service;

    @Test
    void notifyUser_savesUnreadNotificationForUser() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.notifyUser(7L, "hello");

        assertEquals(7L, result.getUserId());
        assertEquals("hello", result.getMessage());
        assertFalse(result.isRead());
    }

    @Test
    void notifyUser_truncatesMessageToColumnLength() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyUser(7L, "x".repeat(300));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(255, captor.getValue().getMessage().length());
    }

    @Test
    void findByUser_unreadNull_returnsAll() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserId(7L, pageable)).thenReturn(page);

        assertSame(page, service.findByUser(7L, null, pageable));
    }

    @Test
    void findByUser_unreadTrue_queriesReadFalse() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserIdAndRead(7L, false, pageable)).thenReturn(page);

        assertSame(page, service.findByUser(7L, true, pageable));
    }

    @Test
    void findByUser_unreadFalse_queriesReadTrue() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserIdAndRead(7L, true, pageable)).thenReturn(page);

        assertSame(page, service.findByUser(7L, false, pageable));
    }

    @Test
    void markRead_setsReadFlag() {
        Notification notification = new Notification(7L, "hi");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        assertTrue(service.markRead(1L).isRead());
    }

    @Test
    void markRead_missing_throwsNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(99L));
    }
}
