package com.laundryhub.repository;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(Long orderId);

    boolean existsBySessionId(Long sessionId);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
