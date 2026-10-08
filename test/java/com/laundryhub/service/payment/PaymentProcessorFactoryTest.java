package com.laundryhub.service.payment;

import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentProcessorFactoryTest {

    private final CashProcessor cash = new CashProcessor();
    private final QrMockProcessor qr = new QrMockProcessor();
    private final PaymentProcessorFactory factory = new PaymentProcessorFactory(List.of(cash, qr));

    @Test
    void returnsProcessorMatchingMethod() {
        assertSame(cash, factory.getProcessor(PaymentMethod.CASH));
        assertSame(qr, factory.getProcessor(PaymentMethod.QR_MOCK));
    }

    @Test
    void unsupportedMethod_throwsBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> factory.getProcessor(PaymentMethod.COIN));
    }

    @Test
    void nullMethod_throwsBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> factory.getProcessor(null));
    }

    @Test
    void duplicateProcessorForSameMethod_failsFast() {
        assertThrows(IllegalStateException.class,
                () -> new PaymentProcessorFactory(List.of(new CashProcessor(), new CashProcessor())));
    }
}
