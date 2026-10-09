package com.laundryhub.service.pricing;

import java.math.BigDecimal;

public record SelfServicePricingInput(
        BigDecimal basePrice,
        BigDecimal pricePerMinute,
        int durationMinutes
) {
}
