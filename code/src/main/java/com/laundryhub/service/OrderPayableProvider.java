package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.service.payment.PayableProvider;
import org.springframework.stereotype.Service;

/**
 * Lets the payment module pay for laundry orders without depending on LaundryOrder or its repository (DIP).
 * CheckoutFacade receives every PayableProvider and picks this one for PayableType.LAUNDRY_ORDER.
 */
@Service
public class OrderPayableProvider implements PayableProvider {

    private final OrderService orderService;

    public OrderPayableProvider(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public PayableType supports() {
        return PayableType.LAUNDRY_ORDER;
    }

    @Override
    public Payable findPayable(Long id) {
        return orderService.findPayable(id);
    }
}
