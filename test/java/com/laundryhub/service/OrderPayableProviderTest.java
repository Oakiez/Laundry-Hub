package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.LaundryOrder;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPayableProviderTest {

    @Mock
    private OrderService orderService;

    private OrderPayableProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OrderPayableProvider(orderService);
    }

    @Test
    void supportsLaundryOrders() {
        assertThat(provider.supports()).isEqualTo(PayableType.LAUNDRY_ORDER);
    }

    @Test
    void findPayable_returnsOrderAsPayable() {
        User customer = new User();
        customer.setId(3L);
        LaundryOrder order = new LaundryOrder();
        order.setId(9L);
        order.setUser(customer);
        order.setTotalAmount(new BigDecimal("70.00"));
        when(orderService.findPayable(9L)).thenReturn(order);

        Payable payable = provider.findPayable(9L);

        assertThat(payable.getId()).isEqualTo(9L);
        assertThat(payable.getPayableAmount()).isEqualByComparingTo("70.00");
        assertThat(payable.getPayableType()).isEqualTo(PayableType.LAUNDRY_ORDER);
        assertThat(payable.getOwnerUserId()).isEqualTo(3L);
    }

    @Test
    void findPayable_unknownOrder_throwsNotFound() {
        when(orderService.findPayable(99L)).thenThrow(new ResourceNotFoundException("Order 99 not found"));

        assertThatThrownBy(() -> provider.findPayable(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}