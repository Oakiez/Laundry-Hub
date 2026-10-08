package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FullServicePricing implements PricingStrategy<FullServicePricingInput> {

    @Override
    public BigDecimal calculate(FullServicePricingInput input) {
        if (input.weightKg() == null || input.weightKg().signum() <= 0) {
            throw new BusinessRuleException("Weight must be greater than 0");
        }
        BigDecimal rate = input.pricePerKg();
        if (input.express()) {
            rate = rate.add(input.expressSurcharge());
        }
        return input.weightKg().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}