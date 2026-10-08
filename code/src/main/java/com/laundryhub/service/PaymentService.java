package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    /**
     * สร้างการชำระเงินสำหรับ payable — จำนวนเงินมาจาก payable เอง
     *
     * @throws com.laundryhub.exception.DuplicateResourceException ถ้า payable นี้มีการชำระแล้ว
     * @throws com.laundryhub.exception.BusinessRuleException      ถ้าไม่รองรับวิธีชำระนี้
     */
    Payment create(Payable payable, PaymentMethod method);

    /**
     * พนักงานยืนยันรับเงิน (ใช้กับ payment ที่ PENDING)
     *
     * @throws com.laundryhub.exception.ResourceNotFoundException   ถ้าไม่พบ payment
     * @throws com.laundryhub.exception.DuplicateResourceException  ถ้าชำระไปแล้ว
     */
    Payment confirm(Long paymentId);

    Payment getById(Long paymentId);

    /** @param status null = ทุกสถานะ */
    Page<Payment> findAll(PaymentStatus status, Pageable pageable);
}
