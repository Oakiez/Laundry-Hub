package com.laundryhub.service.payment;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QrMockProcessorTest {

    private final QrMockProcessor processor = new QrMockProcessor();

    @Test
    void method_isQrMock() {
        assertEquals(PaymentMethod.QR_MOCK, processor.method());
    }

    @Test
    void process_returnsPaidImmediately() {
        Payment payment = Payment.forPayable(payable(), PaymentMethod.QR_MOCK);
        assertEquals(PaymentStatus.PAID, processor.process(payment));
    }

    static Payable payable() {
        return new Payable() {
            public Long getId() { return 1L; }
            public BigDecimal getPayableAmount() { return new BigDecimal("120.00"); }
            public PayableType getPayableType() { return PayableType.LAUNDRY_ORDER; }
            public Long getOwnerUserId() { return 7L; }
        };
    }
}
