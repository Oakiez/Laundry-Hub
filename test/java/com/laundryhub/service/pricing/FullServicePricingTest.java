package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullServicePricingTest {

    private final FullServicePricing pricing = new FullServicePricing();

    @Test
    void normalServiceChargesWeightTimesPricePerKg() {
        var input = new FullServicePricingInput(new BigDecimal("3"), new BigDecimal("25.00"), new BigDecimal("10.00"), false);

        assertThat(pricing.calculate(input)).isEqualByComparingTo("75.00");
    }

    @Test
    void expressServiceAddsSurchargePerKg() {
        var input = new FullServicePricingInput(new BigDecimal("2"), new BigDecimal("25.00"), new BigDecimal("10.00"), true);

        assertThat(pricing.calculate(input)).isEqualByComparingTo("70.00");
    }

    @Test
    void decimalWeightIsRoundedToTwoPlaces() {
        var input = new FullServicePricingInput(new BigDecimal("1.333"), new BigDecimal("25.00"), BigDecimal.ZERO, false);

        assertThat(pricing.calculate(input)).isEqualTo(new BigDecimal("33.33"));
    }

    @Test
    void zeroWeightIsRejected() {
        assertThatThrownBy(() -> new FullServicePricingInput(BigDecimal.ZERO, new BigDecimal("25.00"), BigDecimal.ZERO, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void missingPriceIsRejected() {
        assertThatThrownBy(() -> new FullServicePricingInput(new BigDecimal("2"), null, BigDecimal.ZERO, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nullInputIsRejected() {
        assertThatThrownBy(() -> pricing.calculate(null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
