package com.laundryhub.service.pricing;

import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelfServicePricingTest {

    private final SelfServicePricing pricing = new SelfServicePricing();

    @Test
    void calculatesBasePricePlusTimeCharge() {
        var input = new SelfServicePricingInput(
                new BigDecimal("20.00"), new BigDecimal("1.50"), 30);

        BigDecimal result = pricing.calculate(input);

        assertEquals(0, new BigDecimal("65.00").compareTo(result));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 180})
    void acceptsDurationBoundaries(int minutes) {
        var input = new SelfServicePricingInput(
                BigDecimal.TEN, BigDecimal.ONE, minutes);

        assertEquals(0, BigDecimal.valueOf(10L + minutes)
                .compareTo(pricing.calculate(input)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9, 181})
    void rejectsDurationOutsideBookingRange(int minutes) {
        var input = new SelfServicePricingInput(
                BigDecimal.TEN, BigDecimal.ONE, minutes);

        assertThrows(BusinessRuleException.class,
                () -> pricing.calculate(input));
    }

    @Test
    void rejectsNegativeBasePrice() {
        var input = new SelfServicePricingInput(
                new BigDecimal("-1.00"), BigDecimal.ONE, 30);

        assertThrows(BusinessRuleException.class,
                () -> pricing.calculate(input));
    }

    @Test
    void rejectsNegativeMinutePrice() {
        var input = new SelfServicePricingInput(
                BigDecimal.TEN, new BigDecimal("-0.01"), 30);

        assertThrows(BusinessRuleException.class,
                () -> pricing.calculate(input));
    }

    @Test
    void acceptsZeroPrices() {
        var input = new SelfServicePricingInput(
                BigDecimal.ZERO, BigDecimal.ZERO, 30);

        assertEquals(0, BigDecimal.ZERO.compareTo(pricing.calculate(input)));
    }

    @Test
    void rejectsMissingPricingInformation() {
        assertThrows(BusinessRuleException.class, () -> pricing.calculate(null));
        assertThrows(BusinessRuleException.class, () -> pricing.calculate(
                new SelfServicePricingInput(null, BigDecimal.ONE, 30)));
        assertThrows(BusinessRuleException.class, () -> pricing.calculate(
                new SelfServicePricingInput(BigDecimal.TEN, null, 30)));
    }
}
