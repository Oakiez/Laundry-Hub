package com.laundryhub.controller.web;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.response.BranchResponse;
import com.laundryhub.dto.response.OrderItemResponse;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.dto.response.PageResponse;
import com.laundryhub.dto.response.ServiceTypeResponse;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.security.ApiAccessDeniedHandler;
import com.laundryhub.security.ApiAuthenticationEntryPoint;
import com.laundryhub.security.ApiErrorWriter;
import com.laundryhub.security.AppUserDetails;
import com.laundryhub.service.BranchService;
import com.laundryhub.service.OrderService;
import com.laundryhub.service.ServiceTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Renders the real Thymeleaf order pages with mocked services, and checks the /staff/** rule. */
@WebMvcTest(controllers = OrderWebController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class OrderWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private BranchService branchService;
    @MockitoBean
    private ServiceTypeService serviceTypeService;

    private static AppUserDetails principal(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPassword("HASH");
        user.setRole(role);
        return new AppUserDetails(user);
    }

    private final AppUserDetails customer = principal(3, Role.CUSTOMER);
    private final AppUserDetails staff = principal(2, Role.STAFF);

    private static OrderResponse order(OrderStatus status) {
        OrderItemResponse item = new OrderItemResponse(1L, 1L, "ซักธรรมดา", "เสื้อเชิ้ต", new BigDecimal("2.00"),
                new BigDecimal("70.00"));
        return OrderResponse.builder().id(9L).customerId(3L).branchId(1L).status(status).express(true)
                .totalWeightKg(new BigDecimal("2.00")).totalAmount(new BigDecimal("70.00"))
                .createdAt(LocalDateTime.of(2026, 10, 10, 9, 0)).updatedAt(LocalDateTime.of(2026, 10, 10, 9, 0))
                .items(List.of(item)).build();
    }

    @Test
    void myOrders_listsOwnOrdersWithThaiStatus() throws Exception {
        when(orderService.listForCustomer(eq(3L), eq(null), any()))
                .thenReturn(new PageResponse<>(List.of(order(OrderStatus.WASHING)), 0, 10, 1, 1));

        mockMvc.perform(get("/orders").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/list"))
                .andExpect(content().string(containsString("กำลังซัก")))
                .andExpect(content().string(containsString("70.00")));
    }

    @Test
    void newForm_showsBranchesAndPriceList() throws Exception {
        when(branchService.findAll()).thenReturn(List.of(new BranchResponse(1L, "สาขาหลัก", "addr", "043")));
        when(serviceTypeService.findActive()).thenReturn(List.of(
                new ServiceTypeResponse(1L, "ซักธรรมดา", new BigDecimal("25.00"), new BigDecimal("10.00"), true)));

        mockMvc.perform(get("/orders/new").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("สาขาหลัก")))
                .andExpect(content().string(containsString("ซักธรรมดา")));
    }

    @Test
    void create_withoutItems_showsThaiErrorAndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/orders").with(user(customer)).with(csrf()).param("branchId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/new"))
                .andExpect(content().string(containsString("กรุณากรอกรายการผ้าอย่างน้อย 1 รายการ")));
        verify(orderService, never()).create(any(), any());
    }

    @Test
    void create_valid_ignoresBlankRowsAndRedirectsToDetail() throws Exception {
        when(orderService.create(eq(3L), any())).thenReturn(order(OrderStatus.RECEIVED));

        mockMvc.perform(post("/orders").with(user(customer)).with(csrf())
                        .param("branchId", "1")
                        .param("items[0].serviceTypeId", "1")
                        .param("items[0].itemName", "เสื้อเชิ้ต")
                        .param("items[0].weightKg", "2")
                        .param("items[1].itemName", ""))
                .andExpect(redirectedUrl("/orders/9"))
                .andExpect(flash().attribute("success", containsString("#9")));
        verify(orderService).create(eq(3L), any());
    }

    @Test
    void detail_receivedOrder_showsCancelAndPayButtons() throws Exception {
        when(orderService.getForCustomer(3L, 9L)).thenReturn(order(OrderStatus.RECEIVED));

        mockMvc.perform(get("/orders/9").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ยกเลิกออเดอร์")))
                .andExpect(content().string(containsString("/payments/new?type=LAUNDRY_ORDER&amp;id=9")));
    }

    @Test
    void detail_washingOrder_hidesCancelButton() throws Exception {
        when(orderService.getForCustomer(3L, 9L)).thenReturn(order(OrderStatus.WASHING));

        mockMvc.perform(get("/orders/9").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ยกเลิกออเดอร์"))));
    }

    @Test
    void board_customerIsForbidden() throws Exception {
        mockMvc.perform(get("/staff/orders").with(user(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void board_staffSeesNextStepButtonFromStatePattern() throws Exception {
        when(orderService.listAll(eq(null), any()))
                .thenReturn(new PageResponse<>(List.of(order(OrderStatus.WASHING)), 0, 20, 1, 1));

        // WASHING -> DRYING comes from WashingState.next(), shown as "→ กำลังอบ"
        mockMvc.perform(get("/staff/orders").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("→ กำลังอบ")));
    }

    @Test
    void staffMovesOrderForward_andRuleViolationIsShownAsFlashError() throws Exception {
        when(orderService.changeStatus(9L, ChangeStatusRequest.Action.NEXT)).thenReturn(order(OrderStatus.DRYING));
        mockMvc.perform(post("/staff/orders/9/status").with(user(staff)).with(csrf()).param("action", "NEXT"))
                .andExpect(redirectedUrl("/staff/orders"))
                .andExpect(flash().attribute("success", containsString("กำลังอบ")));

        when(orderService.changeStatus(9L, ChangeStatusRequest.Action.CANCEL))
                .thenThrow(new BusinessRuleException("Order 9 cannot be cancelled once it is DRYING"));
        mockMvc.perform(post("/staff/orders/9/status").with(user(staff)).with(csrf()).param("action", "CANCEL"))
                .andExpect(redirectedUrl("/staff/orders"))
                .andExpect(flash().attribute("error", containsString("cannot be cancelled")));
    }
}
