package com.laundryhub.service.state;

import com.laundryhub.domain.enums.OrderStatus;

public final class OrderStateFactory {

    private OrderStateFactory() {
    }

    public static OrderState from(OrderStatus status) {
        return switch (status) {
            case RECEIVED -> new ReceivedState();
            case WASHING -> new WashingState();
            case DRYING -> new DryingState();
            case IRONING -> new IroningState();
            case READY -> new ReadyState();
            case PICKED_UP -> new PickedUpState();
            case CANCELLED -> new CancelledState();
        };
    }
}