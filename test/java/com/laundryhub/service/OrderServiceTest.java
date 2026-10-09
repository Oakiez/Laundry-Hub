package com.laundryhub.service;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.LaundryOrder;
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
import com.laundryhub.service.impl.OrderServiceImpl;
import com.laundryhub.service.pricing.FullServicePricing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long CUSTOMER_ID = 3L;
    private static final Long ORDER_ID = 9L;

    @Mock
    private LaundryOrderRepository orderRepository;
    @Mock
    private ServiceTypeRepository serviceTypeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // real pricing and mapper: they have no dependencies, so the test also checks the totals end to end
        orderService = new OrderServiceImpl(orderRepository, serviceTypeRepository, userRepository,
                branchRepository, new FullServicePricing(), new OrderMapper(), eventPublisher);
    }

    @Test
    void create_calculatesSubtotalsAndTotalsOnServer() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch()));
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(serviceType(true)));
        when(orderRepository.save(any(LaundryOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // express: (25 + 10) per kg -> 2 kg = 70.00, 1.5 kg = 52.50
        OrderResponse response = orderService.create(CUSTOMER_ID, new CreateOrderRequest(1L, true, null, List.of(
                new OrderItemRequest(1L, "shirts", new BigDecimal("2")),
                new OrderItemRequest(1L, "pants", new BigDecimal("1.5")))));

        assertThat(response.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.items()).extracting("subtotal")
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("52.50"));
        assertThat(response.totalWeightKg()).isEqualByComparingTo("3.5");
        assertThat(response.totalAmount()).isEqualByComparingTo("122.50");
    }

    @Test
    void create_unknownServiceType_throwsNotFoundAndSavesNothing() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch()));
        when(serviceTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(CUSTOMER_ID, new CreateOrderRequest(1L, false, null,
                List.of(new OrderItemRequest(99L, "shirts", new BigDecimal("2"))))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service type 99");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_inactiveServiceType_throwsBusinessRule() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch()));
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(serviceType(false)));

        assertThatThrownBy(() -> orderService.create(CUSTOMER_ID, new CreateOrderRequest(1L, false, null,
                List.of(new OrderItemRequest(1L, "shirts", new BigDecimal("2"))))))
                .isInstanceOf(BusinessRuleException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getForCustomer_orderOfAnotherCustomer_throwsNotFound() {
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.RECEIVED)));

        assertThatThrownBy(() -> orderService.getForCustomer(777L, ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changeStatus_next_movesOneStepAndPublishesEventOnce() {
        LaundryOrder order = order(OrderStatus.RECEIVED);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = orderService.changeStatus(ORDER_ID, ChangeStatusRequest.Action.NEXT);

        assertThat(response.status()).isEqualTo(OrderStatus.WASHING);
        verify(eventPublisher, times(1))
                .publishEvent(new OrderStatusChangedEvent(ORDER_ID, CUSTOMER_ID, OrderStatus.WASHING));
    }

    @Test
    void changeStatus_nextOnPickedUp_throwsAndPublishesNothing() {
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.PICKED_UP)));

        assertThatThrownBy(() -> orderService.changeStatus(ORDER_ID, ChangeStatusRequest.Action.NEXT))
                .isInstanceOf(BusinessRuleException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_cancelAfterWashing_throwsBusinessRule() {
        LaundryOrder order = order(OrderStatus.WASHING);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(ORDER_ID, ChangeStatusRequest.Action.CANCEL))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WASHING);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelByCustomer_whileReceived_cancelsAndPublishesEvent() {
        LaundryOrder order = order(OrderStatus.RECEIVED);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelByCustomer(CUSTOMER_ID, ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publishEvent(new OrderStatusChangedEvent(ORDER_ID, CUSTOMER_ID, OrderStatus.CANCELLED));
    }

    @Test
    void update_afterReceived_throwsBusinessRule() {
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.DRYING)));

        assertThatThrownBy(() -> orderService.update(CUSTOMER_ID, ORDER_ID, new UpdateOrderRequest(false, null,
                List.of(new OrderItemRequest(1L, "shirts", new BigDecimal("2"))))))
                .isInstanceOf(BusinessRuleException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void listForCustomer_withoutStatus_returnsPageOfThatCustomer() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(orderRepository.findByUserId(CUSTOMER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(order(OrderStatus.RECEIVED)), pageable, 5));

        PageResponse<OrderResponse> page = orderService.listForCustomer(CUSTOMER_ID, null, pageable);

        assertThat(page.content()).extracting(OrderResponse::id).containsExactly(ORDER_ID);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void listForCustomer_withStatus_usesStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByUserIdAndStatus(CUSTOMER_ID, OrderStatus.WASHING, pageable))
                .thenReturn(new PageImpl<>(List.of(order(OrderStatus.WASHING)), pageable, 1));

        PageResponse<OrderResponse> page = orderService.listForCustomer(CUSTOMER_ID, OrderStatus.WASHING, pageable);

        assertThat(page.content()).extracting(OrderResponse::status).containsExactly(OrderStatus.WASHING);
        verify(orderRepository, never()).findByUserId(any(), any());
    }

    @Test
    void listAll_unknownSortField_throwsBusinessRuleWithoutQuerying() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> orderService.listAll(null, pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("password");
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void update_whileReceived_replacesItemsAndRecalculatesTotals() {
        LaundryOrder order = order(OrderStatus.RECEIVED);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(serviceType(true)));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        // not express: 3 kg x 25 = 75.00
        OrderResponse response = orderService.update(CUSTOMER_ID, ORDER_ID, new UpdateOrderRequest(false, "new note",
                List.of(new OrderItemRequest(1L, "towels", new BigDecimal("3")))));

        assertThat(response.items()).extracting(item -> item.itemName()).containsExactly("towels");
        assertThat(response.totalAmount()).isEqualByComparingTo("75.00");
        assertThat(response.note()).isEqualTo("new note");
    }

    @Test
    void findPayable_unknownOrder_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findPayable(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User customer() {
        User user = new User();
        user.setId(CUSTOMER_ID);
        user.setRole(Role.CUSTOMER);
        return user;
    }

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(1L);
        return branch;
    }

    private ServiceType serviceType(boolean active) {
        ServiceType serviceType = new ServiceType();
        serviceType.setId(1L);
        serviceType.setName("Wash");
        serviceType.setPricePerKg(new BigDecimal("25.00"));
        serviceType.setExpressSurcharge(new BigDecimal("10.00"));
        serviceType.setActive(active);
        return serviceType;
    }

    private LaundryOrder order(OrderStatus status) {
        LaundryOrder order = new LaundryOrder();
        order.setId(ORDER_ID);
        order.setUser(customer());
        order.setBranch(branch());
        order.setStatus(status);
        return order;
    }
}