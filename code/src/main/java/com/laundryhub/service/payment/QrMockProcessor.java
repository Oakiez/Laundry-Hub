package com.laundryhub.service.payment;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.stereotype.Component;

/** QR จำลอง (ไม่ต่อ Payment Gateway จริง): ถือว่าชำระสำเร็จทันที */
@Component
public class QrMockProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.QR_MOCK;
    }

    @Override
    public PaymentStatus process(Payment payment) {
        return PaymentStatus.PAID;
    }
}
