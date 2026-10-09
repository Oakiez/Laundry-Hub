package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class CancelledState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.empty();
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}