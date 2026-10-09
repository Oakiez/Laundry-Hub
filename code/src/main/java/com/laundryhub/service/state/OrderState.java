package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

import java.util.Optional;

public interface OrderState {

    OrderStatus status();

    Optional<OrderState> next();

    boolean canCancel();
}