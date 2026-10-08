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
import com.laundryhub.service.PaymentService;
import com.laundryhub.service.payment.PaymentProcessorFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessorFactory processorFactory;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentProcessorFactory processorFactory) {
        this.paymentRepository = paymentRepository;
        this.processorFactory = processorFactory;
    }

    @Override
    public Payment create(Payable payable, PaymentMethod method) {
        if (alreadyHasPayment(payable)) {
            throw new DuplicateResourceException(
                    payable.getPayableType() + " " + payable.getId() + " already has a payment");
        }

        Payment payment = Payment.forPayable(payable, method);
        PaymentStatus result = processorFactory.getProcessor(method).process(payment);
        switch (result) {
            case PAID -> payment.markPaid();
            case FAILED -> payment.markFailed();
            case PENDING -> { /* รอพนักงานยืนยัน */ }
        }
        return paymentRepository.save(payment);
    }

    @Override
    public Payment confirm(Long paymentId) {
        Payment payment = getById(paymentId);
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new DuplicateResourceException("Payment " + paymentId + " is already paid");
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessRuleException("Payment " + paymentId + " has failed and cannot be confirmed");
        }
        payment.markPaid();
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findAll(PaymentStatus status, Pageable pageable) {
        return status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);
    }

    private boolean alreadyHasPayment(Payable payable) {
        PayableType type = payable.getPayableType();
        return switch (type) {
            case LAUNDRY_ORDER -> paymentRepository.existsByOrderId(payable.getId());
            case USAGE_SESSION -> paymentRepository.existsBySessionId(payable.getId());
        };
    }
}
