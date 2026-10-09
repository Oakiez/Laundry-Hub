package com.laundryhub.event;

import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.service.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer: ฟังเหตุการณ์จากโมดูลอื่นแล้วสร้างแจ้งเตือน โมดูลออเดอร์/เครื่อง/ชำระเงิน
 * แค่ publishEvent โดยไม่รู้จักระบบแจ้งเตือน (loose coupling).
 *
 * ใช้ @EventListener ธรรมดา (synchronous) จึงรันใน transaction เดียวกับผู้ยิง:
 * ถ้าบันทึกแจ้งเตือนพัง ธุรกรรมหลักจะ rollback ด้วย เลือกแบบนี้เพื่อให้ข้อมูลสอดคล้องกัน
 * (ไม่มีกรณีที่สถานะเปลี่ยนแล้วแต่ไม่มีแจ้งเตือน)
 */
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void on(OrderStatusChangedEvent event) {
        notificationService.notifyUser(event.userId(),
                "ออเดอร์ #" + event.orderId() + " สถานะ: " + event.newStatus());
    }

    @EventListener
    public void on(SessionStatusChangedEvent event) {
        notificationService.notifyUser(event.userId(),
                "การจองเครื่อง #" + event.sessionId() + " สถานะ: " + event.newStatus());
    }

    @EventListener
    public void on(PaymentCompletedEvent event) {
        notificationService.notifyUser(event.userId(),
                "ชำระเงินสำเร็จสำหรับ" + label(event.type()) + " #" + event.refId());
    }

    private static String label(PayableType type) {
        return switch (type) {
            case LAUNDRY_ORDER -> "ออเดอร์";
            case USAGE_SESSION -> "การใช้เครื่อง";
        };
    }
}
