package com.laundryhub.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** No price or total fields on purpose: the server calculates them, a client can never set its own price. */
public record CreateOrderRequest(
        @NotNull Long branchId,
        boolean express,
        @Size(max = 255) String note,
        @NotEmpty @Valid List<OrderItemRequest> items) {
}