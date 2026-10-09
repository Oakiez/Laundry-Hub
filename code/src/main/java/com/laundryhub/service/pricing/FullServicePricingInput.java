package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;

import java.math.BigDecimal;

public record FullServicePricingInput(
        BigDecimal weightKg,
        BigDecimal pricePerKg,
        BigDecimal expressSurcharge,
        boolean express) {

    public FullServicePricingInput {
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new BusinessRuleException("Weight must be greater than 0");
        }
        if (pricePerKg == null || expressSurcharge == null) {
            throw new BusinessRuleException("Price per kg and express surcharge are required");
        }
    }
}