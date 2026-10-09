package com.laundryhub.service;

import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.UpdateOrderRequest;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse create(Long customerId, CreateOrderRequest request);

    OrderResponse getForCustomer(Long customerId, Long orderId);

    /** @param status optional filter, null = all statuses */
    PageResponse<OrderResponse> listForCustomer(Long customerId, OrderStatus status, Pageable pageable);

    /** Staff board: every customer's orders. @param status optional filter, null = all statuses */
    PageResponse<OrderResponse> listAll(OrderStatus status, Pageable pageable);

    OrderResponse update(Long customerId, Long orderId, UpdateOrderRequest request);

    void cancelByCustomer(Long customerId, Long orderId);

    OrderResponse changeStatus(Long orderId, ChangeStatusRequest.Action action);
}