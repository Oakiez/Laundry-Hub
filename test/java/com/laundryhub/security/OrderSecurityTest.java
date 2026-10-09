package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.OrderApiController;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.dto.response.PageResponse;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call which order endpoint. OrderService is mocked, so only the @PreAuthorize rules in
 * OrderApiController, request validation and the error format are under test (same setup as SecurityRulesTest).
 */
@WebMvcTest(controllers = OrderApiController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class OrderSecurityTest {

    private static final String ORDER_JSON =
            "{\"branchId\":1,\"express\":false,\"items\":[{\"serviceTypeId\":1,\"itemName\":\"shirts\",\"weightKg\":2}]}";
    private static final String NEXT_JSON = "{\"action\":\"NEXT\"}";
    private static final String CANCEL_JSON = "{\"action\":\"CANCEL\"}";
    private static final String UPDATE_JSON =
            "{\"express\":true,\"items\":[{\"serviceTypeId\":1,\"itemName\":\"towels\",\"weightKg\":3}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static AppUserDetails principal(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPassword("HASH");
        user.setRole(role);
        return new AppUserDetails(user);
    }

    private final AppUserDetails staff = principal(2, Role.STAFF);
    private final AppUserDetails customer = principal(3, Role.CUSTOMER);

    private static OrderResponse order(OrderStatus status) {
        return OrderResponse.builder().id(9L).customerId(3L).branchId(1L).status(status)
                .totalAmount(new BigDecimal("50.00")).items(List.of()).build();
    }

    // ---------- not logged in ----------

    @Test
    void createOrder_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/customers/3/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(orderService, never()).create(any(), any());
    }

    // ---------- create: owner or staff ----------

    @Test
    void createOrder_ownerForThemselves_returns201() throws Exception {
        when(orderService.create(eq(3L), any())).thenReturn(order(OrderStatus.RECEIVED));

        mockMvc.perform(post("/api/v1/customers/3/orders").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void createOrder_customerForSomeoneElse_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/customers/1/orders").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(orderService, never()).create(any(), any());
    }

    @Test
    void createOrder_staffForACustomer_returns201() throws Exception {
        when(orderService.create(eq(3L), any())).thenReturn(order(OrderStatus.RECEIVED));

        mockMvc.perform(post("/api/v1/customers/3/orders").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_withoutItems_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/customers/3/orders").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"branchId\":1,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
        verify(orderService, never()).create(any(), any());
    }

    // ---------- read one / edit: owner (edit only while RECEIVED, checked in the service) ----------

    @Test
    void getOrder_otherCustomersOrderUnderOwnId_returns404() throws Exception {
        // passes @PreAuthorize (own customerId) but the service hides orders that belong to someone else
        when(orderService.getForCustomer(3L, 99L)).thenThrow(new ResourceNotFoundException("Order 99 not found"));

        mockMvc.perform(get("/api/v1/customers/3/orders/99").with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order 99 not found"));
    }

    @Test
    void updateOrder_owner_returns200() throws Exception {
        when(orderService.update(eq(3L), eq(9L), any())).thenReturn(order(OrderStatus.RECEIVED));

        mockMvc.perform(put("/api/v1/customers/3/orders/9").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void updateOrder_ofAnotherCustomer_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/customers/1/orders/9").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isForbidden());
        verify(orderService, never()).update(any(), any(), any());
    }

    // ---------- list: owner or staff, paged ----------

    @Test
    void listOrders_ownerGetsPagedResult() throws Exception {
        when(orderService.listForCustomer(eq(3L), eq(null), any()))
                .thenReturn(new PageResponse<>(List.of(order(OrderStatus.RECEIVED)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/customers/3/orders").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listOrders_ofAnotherCustomer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1/orders").with(user(customer)))
                .andExpect(status().isForbidden());
    }

    // ---------- staff board and status changes: STAFF/ADMIN only ----------

    @Test
    void staffBoard_customerGets403_staffGets200() throws Exception {
        when(orderService.listAll(eq(null), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/orders").with(user(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders").with(user(staff)))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_customerCannotMoveOwnOrder_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/9/status").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(NEXT_JSON))
                .andExpect(status().isForbidden());
        verify(orderService, never()).changeStatus(any(), any());
    }

    @Test
    void changeStatus_staffMovesOrderToNextStatus() throws Exception {
        when(orderService.changeStatus(9L, ChangeStatusRequest.Action.NEXT)).thenReturn(order(OrderStatus.WASHING));

        mockMvc.perform(patch("/api/v1/orders/9/status").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(NEXT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WASHING"));
    }

    @Test
    void changeStatus_againstStateRules_returns400WithMessage() throws Exception {
        when(orderService.changeStatus(9L, ChangeStatusRequest.Action.NEXT))
                .thenThrow(new BusinessRuleException("Order 9 is already PICKED_UP and cannot move forward"));

        mockMvc.perform(patch("/api/v1/orders/9/status").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(NEXT_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order 9 is already PICKED_UP and cannot move forward"));
    }

    @Test
    void changeStatus_staffCancelsOrder() throws Exception {
        when(orderService.changeStatus(9L, ChangeStatusRequest.Action.CANCEL)).thenReturn(order(OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/orders/9/status").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(CANCEL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- cancel (DELETE): owner only ----------

    @Test
    void cancelOrder_owner_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/3/orders/9").with(user(customer)))
                .andExpect(status().isNoContent());
        verify(orderService).cancelByCustomer(3L, 9L);
    }

    @Test
    void cancelOrder_staffViaCustomerEndpoint_returns403() throws Exception {
        // staff cancel through PATCH /orders/{id}/status instead; DELETE is the customer's own action
        mockMvc.perform(delete("/api/v1/customers/3/orders/9").with(user(staff)))
                .andExpect(status().isForbidden());
        verify(orderService, never()).cancelByCustomer(any(), any());
    }
}
