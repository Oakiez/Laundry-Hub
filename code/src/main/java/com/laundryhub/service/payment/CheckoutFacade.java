package com.laundryhub.service.payment;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.event.PaymentCompletedEvent;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.service.PaymentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade: จุดเข้าเดียวของขั้นตอนชำระเงิน รวมหลายขั้นตอนไว้ให้ Controller เรียกครั้งเดียว
 * (หา payable → ตรวจสิทธิ์ → สร้าง payment → ยิง event).
 * ขึ้นกับ {@link PayableProvider} (interface) ไม่รู้จัก LaundryOrder/UsageSession โดยตรง (DIP).
 */
@Component
public class CheckoutFacade {

    private final Map<PayableType, PayableProvider> providers = new EnumMap<>(PayableType.class);
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMapper paymentMapper;

    public CheckoutFacade(List<PayableProvider> providerList,
                          PaymentService paymentService,
                          ApplicationEventPublisher eventPublisher,
                          PaymentMapper paymentMapper) {
        for (PayableProvider provider : providerList) {
            if (providers.put(provider.supports(), provider) != null) {
                throw new IllegalStateException("Duplicate PayableProvider for " + provider.supports());
            }
        }
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.paymentMapper = paymentMapper;
    }

    /**
     * @param staff true ถ้าผู้เรียกเป็น STAFF/ADMIN (ชำระแทนลูกค้าได้)
     * @throws AccessDeniedException ถ้าไม่ใช่เจ้าของและไม่ใช่พนักงาน
     */
    @Transactional
    public PaymentResponse checkout(CheckoutRequest request, Long currentUserId, boolean staff) {
        Payable payable = providerFor(request.payableType()).findPayable(request.payableId());

        if (!staff && !Objects.equals(payable.getOwnerUserId(), currentUserId)) {
            throw new AccessDeniedException("You can only pay for your own " + request.payableType());
        }

        Payment payment = paymentService.create(payable, request.method());

        if (payment.getStatus() == PaymentStatus.PAID) {
            publishCompleted(payment, payable.getOwnerUserId());
        }
        return paymentMapper.toResponse(payment);
    }

    /** พนักงานยืนยันรับเงินของ payment ที่ค้าง PENDING (เช่น เงินสด) แล้วแจ้งเตือนเจ้าของ */
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        Payment payment = paymentService.confirm(paymentId);
        Payable payable = providerFor(payment.getPayableType()).findPayable(payment.getPayableRefId());
        publishCompleted(payment, payable.getOwnerUserId());
        return paymentMapper.toResponse(payment);
    }

    /** ดู payment ได้เฉพาะเจ้าของ payable หรือพนักงาน */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long currentUserId, boolean staff) {
        Payment payment = paymentService.getById(paymentId);
        if (!staff) {
            Payable payable = providerFor(payment.getPayableType()).findPayable(payment.getPayableRefId());
            if (!Objects.equals(payable.getOwnerUserId(), currentUserId)) {
                throw new AccessDeniedException("You can only view your own payments");
            }
        }
        return paymentMapper.toResponse(payment);
    }

    private PayableProvider providerFor(PayableType type) {
        PayableProvider provider = providers.get(type);
        if (provider == null) {
            throw new BusinessRuleException("Unsupported payable type: " + type);
        }
        return provider;
    }

    private void publishCompleted(Payment payment, Long ownerUserId) {
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                payment.getId(), ownerUserId, payment.getPayableType(), payment.getPayableRefId()));
    }
}
