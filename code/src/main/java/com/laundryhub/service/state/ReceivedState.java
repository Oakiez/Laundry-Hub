package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class ReceivedState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.RECEIVED;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.of(new WashingState());
    }

    @Override
    public boolean canCancel() {
        return true;
    }
}