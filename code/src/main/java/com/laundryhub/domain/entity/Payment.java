package com.laundryhub.domain.entity;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * การชำระเงิน 1 รายการ ผูกกับ order "หรือ" session อย่างใดอย่างหนึ่ง
 * (DB บังคับด้วย CHECK chk_payment_target และ UNIQUE ที่ order_id/session_id).
 * เก็บ FK เป็น Long ไม่ผูก @OneToOne ไปที่ LaundryOrder/UsageSession
 * เพื่อไม่ให้ Payment รู้จักโมดูลอื่นโดยตรง (DIP).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Payment() {
        // required by JPA
    }

    /**
     * สร้าง payment จาก Payable — จำนวนเงินมาจาก {@link Payable#getPayableAmount()} เท่านั้น
     * (ไม่รับจาก client) และตั้ง order_id หรือ session_id ตามชนิดของ Payable
     */
    public static Payment forPayable(Payable payable, PaymentMethod method) {
        Payment payment = new Payment();
        payment.amount = payable.getPayableAmount();
        payment.method = method;
        switch (payable.getPayableType()) {
            case LAUNDRY_ORDER -> payment.orderId = payable.getId();
            case USAGE_SESSION -> payment.sessionId = payable.getId();
        }
        return payment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** เปลี่ยนเป็น PAID พร้อมบันทึกเวลาที่รับเงิน */
    public void markPaid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    /** ชนิดของสิ่งที่ถูกชำระ ดูจาก FK ที่มีค่า (DB รับประกันว่ามีอย่างเดียว) */
    public PayableType getPayableType() {
        return orderId != null ? PayableType.LAUNDRY_ORDER : PayableType.USAGE_SESSION;
    }

    public Long getPayableRefId() {
        return orderId != null ? orderId : sessionId;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
