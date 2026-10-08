package com.laundryhub.dto.response;

import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        PayableType payableType,
        Long payableId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {
}
