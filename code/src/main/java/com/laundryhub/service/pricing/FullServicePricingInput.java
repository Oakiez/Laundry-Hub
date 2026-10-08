package com.laundryhub.service.pricing;

import java.math.BigDecimal;

public record FullServicePricingInput(
        BigDecimal weightKg,
        BigDecimal pricePerKg,
        BigDecimal expressSurcharge,
        boolean express) {
}