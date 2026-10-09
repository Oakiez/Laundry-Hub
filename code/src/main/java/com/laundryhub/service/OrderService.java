package com.laundryhub.service;

import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.response.OrderResponse;

public interface OrderService {

    OrderResponse create(Long customerId, CreateOrderRequest request);

    OrderResponse getForCustomer(Long customerId, Long orderId);
}