package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.NotificationApiController;
import com.laundryhub.controller.api.PaymentApiController;
import com.laundryhub.controller.web.NotificationWebController;
import com.laundryhub.controller.web.PaymentWebController;
import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Notification;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.mapper.NotificationMapper;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.service.NotificationService;
import com.laundryhub.service.PaymentService;
import com.laundryhub.service.payment.CheckoutFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring Security + method security (@PreAuthorize) + Thymeleaf rendering for the payment and notification
 * REST endpoints and web pages. The services are mocked, so no database is needed.
 * (The standalone controller tests do not enable security or render templates; this class covers both.)
 */
@WebMvcTest({PaymentApiController.class, NotificationApiController.class,
        PaymentWebController.class, NotificationWebController.class})
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class, PaymentMapper.class, NotificationMapper.class})
class PaymentNotificationSecurityTest {

    private static final String CHECKOUT_BODY =
            "{\"payableType\":\"LAUNDRY_ORDER\",\"payableId\":5,\"method\":\"QR_MOCK\"}";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    CheckoutFacade checkoutFacade;
    @MockitoBean
    PaymentService paymentService;
    @MockitoBean
    NotificationService notificationService;

    private final AppUserDetails customer = principal(3L, Role.CUSTOMER);
    private final AppUserDetails staff = principal(2L, Role.STAFF);
    private final AppUserDetails admin = principal(1L, Role.ADMIN);

    // ---------- REST: payments ----------

    @Test
    void anonymousGets401_onPaymentList() throws Exception {
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(paymentService, checkoutFacade);
    }

    @Test
    void customerCannotListOrConfirmPayments() throws Exception {
        mvc.perform(get("/api/v1/payments").with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(patch("/api/v1/payments/5/confirm").with(user(customer)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService, checkoutFacade);
    }

    @Test
    void staffCanListAndConfirmPayments() throws Exception {
        when(paymentService.findAll(any(), any())).thenReturn(Page.empty());
        when(checkoutFacade.confirmPayment(5L)).thenReturn(response(PaymentStatus.PAID));

        mvc.perform(get("/api/v1/payments").with(user(staff))).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/payments/5/confirm").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void customerCheckoutUsesOwnIdFromLogin() throws Exception {
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 3L, false)).thenReturn(response(PaymentStatus.PAID));

        mvc.perform(post("/api/v1/payments").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"));
        verify(checkoutFacade).checkout(request, 3L, false);
    }

    // ---------- REST: notifications ----------

    @Test
    void ownerCanListOwnNotifications() throws Exception {
        when(notificationService.findByUser(eq(3L), isNull(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/users/3/notifications").with(user(customer))).andExpect(status().isOk());
    }

    @Test
    void customerCannotListAnotherUsersNotifications() throws Exception {
        mvc.perform(get("/api/v1/users/9/notifications").with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(notificationService);
    }

    @Test
    void adminCanListAnyUsersNotifications() throws Exception {
        when(notificationService.findByUser(eq(9L), isNull(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/users/9/notifications").with(user(admin))).andExpect(status().isOk());
    }

    // ---------- Web pages: rendering ----------

    @Test
    void paymentFormRendersEveryOptionWithText() throws Exception {
        // regression for defect 9: options used to render empty (<option value="CASH"></option>)
        mvc.perform(get("/payments/new").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"LAUNDRY_ORDER\">[^<]+</option>.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"USAGE_SESSION\">[^<]+</option>.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"CASH\">[^<]+</option>.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"QR_MOCK\">[^<]+</option>.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"COIN\">[^<]+</option>.*")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/notifications")));
    }

    @Test
    void staffPaymentListShowsConfirmButtonOnlyForPendingRows() throws Exception {
        Payment pending = payment(5L);
        when(paymentService.findAll(isNull(), any())).thenReturn(new PageImpl<>(List.of(pending)));

        mvc.perform(get("/staff/payments").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/staff/payments/5/confirm")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"PENDING\">[^<]+</option>.*")));
    }

    @Test
    void notificationPageRendersMessageAndReadButtonForUnread() throws Exception {
        Notification unread = new Notification(3L, "paid ok");
        ReflectionTestUtils.setField(unread, "id", 4L);
        when(notificationService.findByUser(eq(3L), isNull(), any())).thenReturn(new PageImpl<>(List.of(unread)));

        mvc.perform(get("/notifications").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("paid ok")))
                .andExpect(content().string(containsString("/notifications/4/read")));
    }

    // ---------- Web pages: security ----------

    @Test
    void customerCannotOpenStaffPaymentPagesOrConfirm() throws Exception {
        mvc.perform(get("/staff/payments").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/payments/5/confirm").with(user(customer)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService, checkoutFacade);
    }

    @Test
    void paymentFormPostWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/payments").with(user(customer))
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "CASH"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkoutFacade);
    }

    @Test
    void paymentFormPostUsesLoggedInUserNotAFormField() throws Exception {
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.CASH);
        when(checkoutFacade.checkout(request, 3L, false)).thenReturn(response(PaymentStatus.PENDING));

        mvc.perform(post("/payments").with(user(customer)).with(csrf())
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "CASH")
                        .param("userId", "99"))
                .andExpect(status().is3xxRedirection());
        verify(checkoutFacade).checkout(request, 3L, false);
    }

    private static AppUserDetails principal(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPassword("HASH");
        user.setRole(role);
        return new AppUserDetails(user);
    }

    private static PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(3L, PayableType.LAUNDRY_ORDER, 5L, new BigDecimal("120.00"),
                PaymentMethod.QR_MOCK, status, null, LocalDateTime.of(2026, 10, 10, 10, 0));
    }

    private static Payment payment(Long id) {
        Payable payable = new Payable() {
            public Long getId() { return 5L; }
            public BigDecimal getPayableAmount() { return new BigDecimal("120.00"); }
            public PayableType getPayableType() { return PayableType.LAUNDRY_ORDER; }
            public Long getOwnerUserId() { return 3L; }
        };
        Payment payment = Payment.forPayable(payable, PaymentMethod.CASH);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
