package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SelfServicePricing
        implements PricingStrategy<SelfServicePricingInput> {

    @Override
    public BigDecimal calculate(SelfServicePricingInput input) {
        if (input == null
                || input.basePrice() == null
                || input.pricePerMinute() == null) {
            throw new BusinessRuleException("Pricing information is required");
        }

        if (input.basePrice().signum() < 0
                || input.pricePerMinute().signum() < 0) {
            throw new BusinessRuleException("Prices must not be negative");
        }

        if (input.durationMinutes() < 10 || input.durationMinutes() > 180) {
            throw new BusinessRuleException(
                    "Duration must be between 10 and 180 minutes");
        }

        BigDecimal timeCharge = input.pricePerMinute()
                .multiply(BigDecimal.valueOf(input.durationMinutes()));

        return input.basePrice().add(timeCharge);
    }
}
