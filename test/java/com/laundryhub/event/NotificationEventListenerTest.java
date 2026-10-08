package com.laundryhub.event;

import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.SessionStatus;
import com.laundryhub.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void orderStatusChanged_notifiesOwnerWithOrderAndStatus() {
        listener.on(new OrderStatusChangedEvent(12L, 7L, OrderStatus.WASHING));

        verify(notificationService).notifyUser(7L, "ออเดอร์ #12 สถานะ: WASHING");
    }

    @Test
    void sessionStatusChanged_notifiesOwnerWithSessionAndStatus() {
        listener.on(new SessionStatusChangedEvent(4L, 9L, SessionStatus.IN_USE));

        verify(notificationService).notifyUser(9L, "การจองเครื่อง #4 สถานะ: IN_USE");
    }

    @Test
    void paymentCompleted_forOrder_notifiesOwner() {
        listener.on(new PaymentCompletedEvent(1L, 7L, PayableType.LAUNDRY_ORDER, 12L));

        verify(notificationService).notifyUser(7L, "ชำระเงินสำเร็จสำหรับออเดอร์ #12");
    }

    @Test
    void paymentCompleted_forSession_notifiesOwner() {
        listener.on(new PaymentCompletedEvent(2L, 9L, PayableType.USAGE_SESSION, 4L));

        verify(notificationService).notifyUser(9L, "ชำระเงินสำเร็จสำหรับการใช้เครื่อง #4");
    }
}
