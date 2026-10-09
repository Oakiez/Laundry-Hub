package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class ReadyState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.READY;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.of(new PickedUpState());
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}