package com.laundryhub.service.impl;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.LaundryOrder;
import com.laundryhub.domain.entity.LaundryOrderItem;
import com.laundryhub.domain.entity.ServiceType;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.OrderItemRequest;
import com.laundryhub.dto.request.UpdateOrderRequest;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.dto.response.PageResponse;
import com.laundryhub.event.OrderStatusChangedEvent;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.OrderMapper;
import com.laundryhub.repository.BranchRepository;
import com.laundryhub.repository.LaundryOrderRepository;
import com.laundryhub.repository.ServiceTypeRepository;
import com.laundryhub.repository.UserRepository;
import com.laundryhub.service.OrderService;
import com.laundryhub.service.pricing.FullServicePricingInput;
import com.laundryhub.service.pricing.PricingStrategy;
import com.laundryhub.service.state.OrderState;
import com.laundryhub.service.state.OrderStateFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "createdAt", "updatedAt", "status", "totalAmount", "totalWeightKg");

    private final LaundryOrderRepository orderRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PricingStrategy<FullServicePricingInput> pricing;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderServiceImpl(LaundryOrderRepository orderRepository, ServiceTypeRepository serviceTypeRepository,
                            UserRepository userRepository, BranchRepository branchRepository,
                            PricingStrategy<FullServicePricingInput> pricing, OrderMapper orderMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.pricing = pricing;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse create(Long customerId, CreateOrderRequest request) {
        User customer = findCustomer(customerId);
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch " + request.branchId() + " not found"));

        LaundryOrder order = new LaundryOrder();
        order.setUser(customer);
        order.setBranch(branch);
        order.setExpress(request.express());
        order.setNote(request.note());
        applyItems(order, request.items());

        // cascade = ALL on items: saving the order also inserts every item
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    public OrderResponse getForCustomer(Long customerId, Long orderId) {
        return orderMapper.toResponse(findOwnedOrder(customerId, orderId));
    }

    @Override
    public PageResponse<OrderResponse> listForCustomer(Long customerId, OrderStatus status, Pageable pageable) {
        checkSortable(pageable);
        Page<LaundryOrder> page = status == null
                ? orderRepository.findByUserId(customerId, pageable)
                : orderRepository.findByUserIdAndStatus(customerId, status, pageable);
        return PageResponse.from(page.map(orderMapper::toResponse));
    }

    @Override
    public PageResponse<OrderResponse> listAll(OrderStatus status, Pageable pageable) {
        checkSortable(pageable);
        Page<LaundryOrder> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(orderMapper::toResponse));
    }

    @Override
    @Transactional
    public OrderResponse update(Long customerId, Long orderId, UpdateOrderRequest request) {
        LaundryOrder order = findOwnedOrder(customerId, orderId);
        if (order.getStatus() != OrderStatus.RECEIVED) {
            throw new BusinessRuleException("Order " + orderId + " can only be edited while RECEIVED");
        }
        order.setExpress(request.express());
        order.setNote(request.note());
        // orphanRemoval = true: the old item rows are deleted, the new ones inserted
        order.clearItems();
        applyItems(order, request.items());

        // flush now so @PreUpdate sets updatedAt before we build the response
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Override
    @Transactional
    public void cancelByCustomer(Long customerId, Long orderId) {
        cancel(findOwnedOrder(customerId, orderId));
    }

    @Override
    @Transactional
    public OrderResponse changeStatus(Long orderId, ChangeStatusRequest.Action action) {
        LaundryOrder order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
        switch (action) {
            case NEXT -> advance(order);
            case CANCEL -> cancel(order);
        }
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Override
    public Payable findPayable(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    // State pattern: the current state decides what comes next, no if/else chain over statuses here
    private void advance(LaundryOrder order) {
        OrderState next = OrderStateFactory.from(order.getStatus()).next()
                .orElseThrow(() -> new BusinessRuleException(
                        "Order " + order.getId() + " is already " + order.getStatus() + " and cannot move forward"));
        order.setStatus(next.status());
        publishStatusChanged(order);
    }

    private void cancel(LaundryOrder order) {
        if (!OrderStateFactory.from(order.getStatus()).canCancel()) {
            throw new BusinessRuleException(
                    "Order " + order.getId() + " cannot be cancelled once it is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        publishStatusChanged(order);
    }

    // Observer: NotificationEventListener (Payment/Notification module) listens and creates the notification
    private void publishStatusChanged(LaundryOrder order) {
        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(order.getId(), order.getUser().getId(), order.getStatus()));
    }

    // prices every item and recalculates the totals on the server; totals are never taken from the client
    private void applyItems(LaundryOrder order, List<OrderItemRequest> itemRequests) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : itemRequests) {
            ServiceType serviceType = findActiveServiceType(itemRequest.serviceTypeId());

            LaundryOrderItem item = new LaundryOrderItem();
            item.setServiceType(serviceType);
            item.setItemName(itemRequest.itemName());
            item.setWeightKg(itemRequest.weightKg());
            item.setSubtotal(pricing.calculate(new FullServicePricingInput(
                    itemRequest.weightKg(),
                    serviceType.getPricePerKg(),
                    serviceType.getExpressSurcharge(),
                    order.isExpress())));
            order.addItem(item);

            totalWeight = totalWeight.add(item.getWeightKg());
            totalAmount = totalAmount.add(item.getSubtotal());
        }
        order.setTotalWeightKg(totalWeight);
        order.setTotalAmount(totalAmount);
    }

    // an unknown sort field would fail deep inside Spring Data as a 500; reject it up front as a 400
    private void checkSortable(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessRuleException("Cannot sort orders by '" + order.getProperty() + "'");
            }
        });
    }

    private User findCustomer(Long customerId) {
        return userRepository.findById(customerId)
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " not found"));
    }

    private ServiceType findActiveServiceType(Long serviceTypeId) {
        ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Service type " + serviceTypeId + " not found"));
        if (!serviceType.isActive()) {
            throw new BusinessRuleException("Service type " + serviceTypeId + " is not available");
        }
        return serviceType;
    }

    private LaundryOrder findOwnedOrder(Long customerId, Long orderId) {
        LaundryOrder order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
        // 404 (not 403) so a customer cannot probe which order ids belong to other people
        if (!order.getUser().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Order " + orderId + " not found");
        }
        return order;
    }
}