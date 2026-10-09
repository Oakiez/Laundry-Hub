package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class WashingState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.WASHING;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.of(new DryingState());
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}