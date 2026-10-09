package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class DryingState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.DRYING;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.of(new IroningState());
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}