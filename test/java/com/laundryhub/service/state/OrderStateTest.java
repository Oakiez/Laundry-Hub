package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStateTest {

    @ParameterizedTest
    @CsvSource({
            "RECEIVED, WASHING",
            "WASHING, DRYING",
            "DRYING, IRONING",
            "IRONING, READY",
            "READY, PICKED_UP"
    })
    void nextMovesOneStepForward(OrderStatus current, OrderStatus expectedNext) {
        OrderState state = OrderStateFactory.from(current);

        assertThat(state.next()).map(OrderState::status).contains(expectedNext);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PICKED_UP", "CANCELLED"})
    void finalStatesHaveNoNext(OrderStatus status) {
        assertThat(OrderStateFactory.from(status).next()).isEmpty();
    }

    @Test
    void onlyReceivedCanBeCancelled() {
        for (OrderStatus status : OrderStatus.values()) {
            boolean expected = status == OrderStatus.RECEIVED;
            assertThat(OrderStateFactory.from(status).canCancel()).as(status.name()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void factoryReturnsStateMatchingStatus(OrderStatus status) {
        assertThat(OrderStateFactory.from(status).status()).isEqualTo(status);
    }
}