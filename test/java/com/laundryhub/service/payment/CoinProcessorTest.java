package com.laundryhub.service.payment;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoinProcessorTest {

    private final CoinProcessor processor = new CoinProcessor();

    @Test
    void method_isCoin() {
        assertEquals(PaymentMethod.COIN, processor.method());
    }

    @Test
    void process_machineSession_isPaidImmediately() {
        Payment payment = Payment.forPayable(payable(PayableType.USAGE_SESSION), PaymentMethod.COIN);

        assertEquals(PaymentStatus.PAID, processor.process(payment));
    }

    @Test
    void process_laundryOrder_isRejected() {
        Payment payment = Payment.forPayable(payable(PayableType.LAUNDRY_ORDER), PaymentMethod.COIN);

        assertThrows(BusinessRuleException.class, () -> processor.process(payment));
    }

    private static Payable payable(PayableType type) {
        return new Payable() {
            public Long getId() { return 1L; }
            public BigDecimal getPayableAmount() { return new BigDecimal("40.00"); }
            public PayableType getPayableType() { return type; }
            public Long getOwnerUserId() { return 7L; }
        };
    }
}
