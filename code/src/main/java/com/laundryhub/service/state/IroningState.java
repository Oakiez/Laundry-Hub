package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public class IroningState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.IRONING;
    }

    @Override
    public Optional<OrderState> next() {
        return Optional.of(new ReadyState());
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}