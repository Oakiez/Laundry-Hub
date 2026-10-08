package com.laundryhub.service.impl;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.repository.PaymentRepository;
import com.laundryhub.service.payment.CashProcessor;
import com.laundryhub.service.payment.PaymentProcessorFactory;
import com.laundryhub.service.payment.QrMockProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        PaymentProcessorFactory factory =
                new PaymentProcessorFactory(List.of(new CashProcessor(), new QrMockProcessor()));
        service = new PaymentServiceImpl(paymentRepository, factory);
    }

    @Test
    void create_qrMock_isPaidImmediately() {
        when(paymentRepository.existsByOrderId(5L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.create(order(5L, "120.00"), PaymentMethod.QR_MOCK);

        assertEquals(PaymentStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        assertEquals(new BigDecimal("120.00"), result.getAmount());
        assertEquals(5L, result.getOrderId());
    }

    @Test
    void create_cash_staysPending() {
        when(paymentRepository.existsByOrderId(5L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.create(order(5L, "120.00"), PaymentMethod.CASH);

        assertEquals(PaymentStatus.PENDING, result.getStatus());
    }

    @Test
    void create_sessionPayable_checksSessionId() {
        when(paymentRepository.existsBySessionId(8L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(session(8L), PaymentMethod.QR_MOCK));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void create_alreadyPaid_throwsDuplicate_andDoesNotSave() {
        when(paymentRepository.existsByOrderId(5L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(order(5L, "120.00"), PaymentMethod.QR_MOCK));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void create_unsupportedMethod_throwsBusinessRule() {
        when(paymentRepository.existsByOrderId(5L)).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> service.create(order(5L, "120.00"), PaymentMethod.COIN));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirm_pending_becomesPaid() {
        Payment pending = Payment.forPayable(order(5L, "50.00"), PaymentMethod.CASH);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.confirm(1L);

        assertEquals(PaymentStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
    }

    @Test
    void confirm_alreadyPaid_throwsDuplicate() {
        Payment paid = Payment.forPayable(order(5L, "50.00"), PaymentMethod.QR_MOCK);
        paid.markPaid();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(paid));

        assertThrows(DuplicateResourceException.class, () -> service.confirm(1L));
    }

    @Test
    void confirm_failed_throwsBusinessRule() {
        Payment failed = Payment.forPayable(order(5L, "50.00"), PaymentMethod.CASH);
        failed.markFailed();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(failed));

        assertThrows(BusinessRuleException.class, () -> service.confirm(1L));
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(99L));
    }

    private static Payable order(Long id, String amount) {
        return payable(PayableType.LAUNDRY_ORDER, id, amount);
    }

    private static Payable session(Long id) {
        return payable(PayableType.USAGE_SESSION, id, "30.00");
    }

    private static Payable payable(PayableType type, Long id, String amount) {
        return new Payable() {
            public Long getId() { return id; }
            public BigDecimal getPayableAmount() { return new BigDecimal(amount); }
            public PayableType getPayableType() { return type; }
            public Long getOwnerUserId() { return 7L; }
        };
    }
}
