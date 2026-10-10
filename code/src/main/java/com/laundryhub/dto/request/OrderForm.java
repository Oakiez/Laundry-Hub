package com.laundryhub.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing object for the "new order" web form. The page shows a fixed number of item rows,
 * so this class is mutable (Thymeleaf binds items[0].weightKg ...) and converts itself to the
 * same CreateOrderRequest the REST API uses, so both paths share one set of validation rules.
 */
@Getter
@Setter
public class OrderForm {

    public static final int ITEM_ROWS = 3;

    private Long branchId;
    private boolean express;
    private String note;
    private List<Item> items = new ArrayList<>();

    public OrderForm() {
        for (int i = 0; i < ITEM_ROWS; i++) {
            items.add(new Item());
        }
    }

    /** Rows the customer left completely empty are ignored. */
    public CreateOrderRequest toRequest() {
        List<OrderItemRequest> filled = items.stream()
                .filter(item -> !item.isBlank())
                .map(item -> new OrderItemRequest(item.getServiceTypeId(), item.getItemName(), item.getWeightKg()))
                .toList();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        return new CreateOrderRequest(branchId, express, cleanNote, filled);
    }

    @Getter
    @Setter
    public static class Item {
        private Long serviceTypeId;
        private String itemName;
        private BigDecimal weightKg;

        boolean isBlank() {
            return serviceTypeId == null && weightKg == null && (itemName == null || itemName.isBlank());
        }
    }
}
