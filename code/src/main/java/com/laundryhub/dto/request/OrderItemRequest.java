package com.laundryhub.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull Long serviceTypeId,
        @NotBlank @Size(max = 100) String itemName,
        // NUMERIC(8,2) in laundry_order_items.weight_kg
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal weightKg) {
}