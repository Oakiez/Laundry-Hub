package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.LaundryOrder;
import com.laundryhub.domain.entity.LaundryOrderItem;
import com.laundryhub.domain.entity.ServiceType;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.OrderItemRequest;
import com.laundryhub.dto.response.OrderResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final LaundryOrderRepository orderRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PricingStrategy<FullServicePricingInput> pricing;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(LaundryOrderRepository orderRepository, ServiceTypeRepository serviceTypeRepository,
                            UserRepository userRepository, BranchRepository branchRepository,
                            PricingStrategy<FullServicePricingInput> pricing, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.pricing = pricing;
        this.orderMapper = orderMapper;
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
