package com.laundryhub.mapper;

import com.laundryhub.domain.entity.LaundryOrder;
import com.laundryhub.domain.entity.LaundryOrderItem;
import com.laundryhub.dto.response.OrderItemResponse;
import com.laundryhub.dto.response.OrderResponse;
import org.springframework.stereotype.Component;

/** Entity -> response only. Building an order from a request needs DB lookups, so that lives in OrderService. */
@Component
public class OrderMapper {

    public OrderResponse toResponse(LaundryOrder order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getUser().getId())
                .branchId(order.getBranch().getId())
                .status(order.getStatus())
                .express(order.isExpress())
                .totalWeightKg(order.getTotalWeightKg())
                .totalAmount(order.getTotalAmount())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    public OrderItemResponse toItemResponse(LaundryOrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getServiceType().getId(),
                item.getServiceType().getName(),
                item.getItemName(),
                item.getWeightKg(),
                item.getSubtotal());
    }
}