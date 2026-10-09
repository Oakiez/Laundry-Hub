package com.laundryhub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** @param active optional: null keeps the current value on update and means "active" on create */
public record ServiceTypeRequest(
        @NotBlank @Size(max = 100) String name,
        // NUMERIC(10,2) and CHECK (>= 0) in service_types
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal expressSurcharge,
        Boolean active) {
}
