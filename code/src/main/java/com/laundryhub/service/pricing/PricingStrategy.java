package com.laundryhub.service.pricing;

import java.math.BigDecimal;

public interface PricingStrategy<I> {

    BigDecimal calculate(I input);
}
