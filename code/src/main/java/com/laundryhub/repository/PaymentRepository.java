package com.laundryhub.repository;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(Long orderId);

    boolean existsBySessionId(Long sessionId);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    /** Which of these usage sessions already have a payment (any status), for hiding the pay button. */
    @Query("select p.sessionId from Payment p where p.sessionId in :ids")
    List<Long> findSessionIdsWithPayment(@Param("ids") Collection<Long> ids);
}
