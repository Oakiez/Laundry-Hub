package com.laundryhub.event;

import com.laundryhub.domain.enums.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, Long userId, OrderStatus newStatus) {
}
