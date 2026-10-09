package com.laundryhub.service.payment;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashProcessorTest {

    private final CashProcessor processor = new CashProcessor();

    @Test
    void method_isCash() {
        assertEquals(PaymentMethod.CASH, processor.method());
    }

    @Test
    void process_staysPendingUntilStaffConfirms() {
        Payment payment = Payment.forPayable(QrMockProcessorTest.payable(), PaymentMethod.CASH);
        assertEquals(PaymentStatus.PENDING, processor.process(payment));
    }
}
