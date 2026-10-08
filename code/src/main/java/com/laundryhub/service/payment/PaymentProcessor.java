package com.laundryhub.service.payment;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;

/** Strategy: วิธีชำระเงินแต่ละแบบเป็น implementation ของ interface นี้ */
public interface PaymentProcessor {

    /** วิธีชำระที่ processor ตัวนี้รับผิดชอบ (Factory ใช้เป็น key) */
    PaymentMethod method();

    /** ประมวลผลแล้วคืนสถานะที่ควรเป็น */
    PaymentStatus process(Payment payment);
}
