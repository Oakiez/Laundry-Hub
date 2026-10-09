package com.laundryhub.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long serviceTypeId,
        String serviceTypeName,
        String itemName,
        BigDecimal weightKg,
        BigDecimal subtotal) {
}
