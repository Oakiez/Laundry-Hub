package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Set;

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

    /** true ถ้าออเดอร์นี้มีการชำระเงินแล้ว (PENDING/PAID) ใช้ซ่อนปุ่มชำระเงินในหน้าเว็บ */
    boolean orderHasPayment(Long orderId);

    /** รหัสรอบใช้งานในชุดที่ส่งมาซึ่งมีการชำระเงินแล้ว (ถามครั้งเดียวต่อหน้า ไม่ query ทีละแถว) */
    Set<Long> sessionIdsWithPayment(Collection<Long> sessionIds);

    /** @param status null = ทุกสถานะ */
    Page<Payment> findAll(PaymentStatus status, Pageable pageable);
}
