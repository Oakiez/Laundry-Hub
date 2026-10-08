package com.laundryhub.service.payment;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.stereotype.Component;

/** เงินสด: ยังไม่ถือว่าจ่ายจนกว่าพนักงานกดยืนยันรับเงิน จึงคืน PENDING */
@Component
public class CashProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public PaymentStatus process(Payment payment) {
        return PaymentStatus.PENDING;
    }
}
