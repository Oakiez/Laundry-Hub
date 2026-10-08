package com.laundryhub.domain.entity;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentTest {

    @Test
    void forPayable_order_setsOrderIdOnly_andTakesAmountFromPayable() {
        Payment p = Payment.forPayable(payable(PayableType.LAUNDRY_ORDER, 5L, "99.50"), PaymentMethod.CASH);

        assertEquals(5L, p.getOrderId());
        assertNull(p.getSessionId());
        assertEquals(new BigDecimal("99.50"), p.getAmount());
        assertEquals(PaymentStatus.PENDING, p.getStatus());
    }

    @Test
    void forPayable_session_setsSessionIdOnly() {
        Payment p = Payment.forPayable(payable(PayableType.USAGE_SESSION, 8L, "30.00"), PaymentMethod.QR_MOCK);

        assertEquals(8L, p.getSessionId());
        assertNull(p.getOrderId());
    }

    @Test
    void markPaid_setsStatusAndPaidAt() {
        Payment p = Payment.forPayable(payable(PayableType.LAUNDRY_ORDER, 1L, "10.00"), PaymentMethod.CASH);
        p.markPaid();

        assertEquals(PaymentStatus.PAID, p.getStatus());
        assertNotNull(p.getPaidAt());
    }

    @Test
    void markPaid_calledTwice_keepsFirstPaidAt() throws InterruptedException {
        Payment p = Payment.forPayable(payable(PayableType.LAUNDRY_ORDER, 1L, "10.00"), PaymentMethod.CASH);
        p.markPaid();
        var firstPaidAt = p.getPaidAt();
        Thread.sleep(5);
        p.markPaid();

        assertEquals(firstPaidAt, p.getPaidAt());
    }

    private static Payable payable(PayableType type, Long id, String amount) {
        return new Payable() {
            public Long getId() { return id; }
            public BigDecimal getPayableAmount() { return new BigDecimal(amount); }
            public PayableType getPayableType() { return type; }
            public Long getOwnerUserId() { return 1L; }
        };
    }
}
