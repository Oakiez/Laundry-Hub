package com.laundryhub.service.payment;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.event.PaymentCompletedEvent;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutFacadeTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private PayableProvider orderProvider;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CheckoutFacade facade;

    @BeforeEach
    void setUp() {
        when(orderProvider.supports()).thenReturn(PayableType.LAUNDRY_ORDER);
        facade = new CheckoutFacade(List.of(orderProvider), paymentService, eventPublisher, new PaymentMapper());
    }

    @Test
    void checkout_paid_publishesPaymentCompletedEvent() {
        Payable payable = order(5L);
        when(orderProvider.findPayable(5L)).thenReturn(payable);
        when(paymentService.create(payable, PaymentMethod.QR_MOCK)).thenReturn(paymentFor(payable, true));

        PaymentResponse response = facade.checkout(request(PaymentMethod.QR_MOCK), OWNER_ID, false);

        assertEquals(PaymentStatus.PAID, response.status());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PaymentCompletedEvent event = (PaymentCompletedEvent) captor.getValue();
        assertEquals(OWNER_ID, event.userId());
        assertEquals(PayableType.LAUNDRY_ORDER, event.type());
        assertEquals(5L, event.refId());
    }

    @Test
    void checkout_pending_doesNotPublishEvent() {
        Payable payable = order(5L);
        when(orderProvider.findPayable(5L)).thenReturn(payable);
        when(paymentService.create(payable, PaymentMethod.CASH)).thenReturn(paymentFor(payable, false));

        PaymentResponse response = facade.checkout(request(PaymentMethod.CASH), OWNER_ID, false);

        assertEquals(PaymentStatus.PENDING, response.status());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkout_notOwner_throwsAccessDenied_andDoesNotCreatePayment() {
        when(orderProvider.findPayable(5L)).thenReturn(order(5L));

        assertThrows(AccessDeniedException.class,
                () -> facade.checkout(request(PaymentMethod.QR_MOCK), OTHER_USER_ID, false));

        verifyNoInteractions(paymentService, eventPublisher);
    }

    @Test
    void checkout_staff_canPayForAnotherUser() {
        Payable payable = order(5L);
        when(orderProvider.findPayable(5L)).thenReturn(payable);
        when(paymentService.create(payable, PaymentMethod.CASH)).thenReturn(paymentFor(payable, false));

        PaymentResponse response = facade.checkout(request(PaymentMethod.CASH), OTHER_USER_ID, true);

        assertEquals(PaymentStatus.PENDING, response.status());
    }

    @Test
    void checkout_alreadyPaid_propagatesDuplicate_andDoesNotPublish() {
        Payable payable = order(5L);
        when(orderProvider.findPayable(5L)).thenReturn(payable);
        when(paymentService.create(payable, PaymentMethod.QR_MOCK))
                .thenThrow(new DuplicateResourceException("already paid"));

        assertThrows(DuplicateResourceException.class,
                () -> facade.checkout(request(PaymentMethod.QR_MOCK), OWNER_ID, false));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkout_noProviderForType_throwsBusinessRule() {
        CheckoutRequest sessionRequest = new CheckoutRequest(PayableType.USAGE_SESSION, 1L, PaymentMethod.CASH);

        assertThrows(BusinessRuleException.class, () -> facade.checkout(sessionRequest, OWNER_ID, false));

        verify(orderProvider, never()).findPayable(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    void confirmCash_publishesEventToOwner() {
        Payable payable = order(5L);
        when(paymentService.confirm(3L)).thenReturn(paymentFor(payable, true));
        when(orderProvider.findPayable(5L)).thenReturn(payable);

        PaymentResponse response = facade.confirmCash(3L);

        assertEquals(PaymentStatus.PAID, response.status());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(OWNER_ID, ((PaymentCompletedEvent) captor.getValue()).userId());
    }

    private static CheckoutRequest request(PaymentMethod method) {
        return new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, method);
    }

    private static Payment paymentFor(Payable payable, boolean paid) {
        Payment payment = Payment.forPayable(payable, paid ? PaymentMethod.QR_MOCK : PaymentMethod.CASH);
        if (paid) {
            payment.markPaid();
        }
        return payment;
    }

    private static Payable order(Long id) {
        return new Payable() {
            public Long getId() { return id; }
            public BigDecimal getPayableAmount() { return new BigDecimal("120.00"); }
            public PayableType getPayableType() { return PayableType.LAUNDRY_ORDER; }
            public Long getOwnerUserId() { return OWNER_ID; }
        };
    }
}
