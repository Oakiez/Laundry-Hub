package com.laundryhub.service;

import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.UpdateOrderRequest;
import com.laundryhub.dto.response.OrderResponse;

public interface OrderService {

    OrderResponse create(Long customerId, CreateOrderRequest request);

    OrderResponse getForCustomer(Long customerId, Long orderId);

    OrderResponse update(Long customerId, Long orderId, UpdateOrderRequest request);

    void cancelByCustomer(Long customerId, Long orderId);

    OrderResponse changeStatus(Long orderId, ChangeStatusRequest.Action action);
}