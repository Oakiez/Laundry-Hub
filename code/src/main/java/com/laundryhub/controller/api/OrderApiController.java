package com.laundryhub.controller.api;

import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.UpdateOrderRequest;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.dto.response.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders")
public class OrderApiController {

    // #customerId is the path variable, principal.id is the logged-in user's id (AppUserDetails.getId())
    private static final String OWNER = "#customerId == authentication.principal.id";
    private static final String OWNER_OR_STAFF = "hasAnyRole('STAFF','ADMIN') or " + OWNER;

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/customers/{customerId}/orders")
    @PreAuthorize(OWNER_OR_STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a laundry order (owner or staff)")
    public OrderResponse create(@PathVariable Long customerId, @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(customerId, request);
    }

    @GetMapping("/customers/{customerId}/orders/{orderId}")
    @PreAuthorize(OWNER_OR_STAFF)
    @Operation(summary = "Get an order with its items (owner or staff)")
    public OrderResponse get(@PathVariable Long customerId, @PathVariable Long orderId) {
        return orderService.getForCustomer(customerId, orderId);
    }

    
    @GetMapping("/customers/{customerId}/orders")
    @PreAuthorize(OWNER_OR_STAFF)
    @Operation(summary = "List a customer's orders, paged and sorted (owner or staff)",
            description = "Example: ?page=0&size=10&sort=createdAt,desc&status=WASHING")
    public PageResponse<OrderResponse> listForCustomer(
            @PathVariable Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return orderService.listForCustomer(customerId, status, pageable);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Staff board: all orders, paged and sorted (staff)",
            description = "Example: ?status=RECEIVED&page=0&size=20&sort=createdAt,asc")
    public PageResponse<OrderResponse> listAll(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return orderService.listAll(status, pageable);
    }

    @PutMapping("/customers/{customerId}/orders/{orderId}")
    @PreAuthorize(OWNER)
    @Operation(summary = "Edit an order while it is RECEIVED (owner)")
    public OrderResponse update(@PathVariable Long customerId, @PathVariable Long orderId,
                                @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.update(customerId, orderId, request);
    }

    @DeleteMapping("/customers/{customerId}/orders/{orderId}")
    @PreAuthorize(OWNER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel an order while it is RECEIVED (owner)")
    public void cancel(@PathVariable Long customerId, @PathVariable Long orderId) {
        orderService.cancelByCustomer(customerId, orderId);
    }

    @PatchMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Move an order to its next status or cancel it (staff)")
    public OrderResponse changeStatus(@PathVariable Long orderId, @Valid @RequestBody ChangeStatusRequest request) {
        return orderService.changeStatus(orderId, request.action());
    }
}