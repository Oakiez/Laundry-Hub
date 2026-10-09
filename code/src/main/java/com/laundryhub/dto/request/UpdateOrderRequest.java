package com.laundryhub.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Allowed only while the order is still RECEIVED; replaces all items. */
public record UpdateOrderRequest(
        boolean express,
        @Size(max = 255) String note,
        @NotEmpty @Valid List<OrderItemRequest> items) {
}
