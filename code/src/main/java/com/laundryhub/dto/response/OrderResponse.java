package com.laundryhub.dto.response;

import com.laundryhub.domain.enums.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// many fields -> Builder keeps construction readable (OrderResponse.builder().id(..).status(..).build())
@Builder
public record OrderResponse(
        Long id,
        Long customerId,
        Long branchId,
        OrderStatus status,
        boolean express,
        BigDecimal totalWeightKg,
        BigDecimal totalAmount,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemResponse> items) {
}