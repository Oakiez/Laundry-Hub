package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Price of one laundry item: weightKg × (pricePerKg + expressSurcharge if express), rounded to 2 decimals (HALF_UP).
 * expressSurcharge is baht per kg, e.g. 2 kg express at 25.00 + 10.00 = 70.00.
 * Input values are validated in {@link FullServicePricingInput}.
 */
@Component
public class FullServicePricing implements PricingStrategy<FullServicePricingInput> {

    @Override
    public BigDecimal calculate(FullServicePricingInput input) {
        if (input == null) {
            throw new BusinessRuleException("Pricing input is required");
        }
        BigDecimal rate = input.pricePerKg();
        if (input.express()) {
            rate = rate.add(input.expressSurcharge());
        }
        return input.weightKg().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}