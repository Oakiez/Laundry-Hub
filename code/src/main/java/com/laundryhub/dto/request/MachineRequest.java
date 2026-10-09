package com.laundryhub.dto.request;

import com.laundryhub.domain.enums.MachineType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MachineRequest(
        @NotNull @Positive Long branchId,
        @NotBlank @Size(max = 50) String name,
        @NotNull MachineType machineType,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal pricePerMinute) {
}
