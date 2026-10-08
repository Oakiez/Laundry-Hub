package com.laundryhub.mapper;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

/** แปลง Entity → DTO เพื่อไม่ให้ Entity หลุดออกไปเป็น API contract */
@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPayableType(),
                payment.getPayableRefId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
