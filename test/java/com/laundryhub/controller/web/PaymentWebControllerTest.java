package com.laundryhub.controller.web;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.security.AppUserDetails;
import com.laundryhub.service.PaymentService;
import com.laundryhub.service.payment.CheckoutFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Controller-level test of the payment pages (standalone MockMvc, no template rendering): checks the view name,
 * model, flash messages and how the current user/role is passed to the Facade.
 * Templates are checked by opening the pages in the running app.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebControllerTest {

    @Mock
    private CheckoutFacade checkoutFacade;
    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noRender = (model, request, response) -> { };
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentWebController(checkoutFacade, paymentService, new PaymentMapper()))
                .setViewResolvers((viewName, locale) -> noRender)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newForm_prefillsTypeAndIdFromQuery() throws Exception {
        loginAs(7L, Role.CUSTOMER);

        mockMvc.perform(get("/payments/new").param("type", "LAUNDRY_ORDER").param("id", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/new"))
                .andExpect(model().attribute("form", new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, null)));
    }

    @Test
    void checkout_paid_redirectsToDetailWithSuccessMessage() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 7L, false)).thenReturn(response(PaymentStatus.PAID));

        mockMvc.perform(post("/payments")
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "QR_MOCK"))
                .andExpect(view().name("redirect:/payments/3"))
                .andExpect(flash().attribute("success", "ชำระเงินเรียบร้อยแล้ว"));
    }

    @Test
    void checkout_cash_pending_tellsUserToWaitForStaff() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.CASH);
        when(checkoutFacade.checkout(request, 7L, false)).thenReturn(response(PaymentStatus.PENDING));

        mockMvc.perform(post("/payments")
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "CASH"))
                .andExpect(view().name("redirect:/payments/3"))
                .andExpect(flash().attribute("success", "บันทึกการชำระแล้ว กรุณารอพนักงานยืนยันการรับเงิน"));
    }

    @Test
    void checkout_asStaff_passesStaffTrue() throws Exception {
        loginAs(2L, Role.STAFF);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.CASH);
        when(checkoutFacade.checkout(request, 2L, true)).thenReturn(response(PaymentStatus.PENDING));

        mockMvc.perform(post("/payments")
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "CASH"))
                .andExpect(view().name("redirect:/payments/3"));
    }

    @Test
    void checkout_missingFields_showsFormErrors_andDoesNotCallFacade() throws Exception {
        loginAs(7L, Role.CUSTOMER);

        mockMvc.perform(post("/payments"))
                .andExpect(view().name("payments/new"))
                .andExpect(model().attributeHasFieldErrors("form", "payableType", "payableId", "method"));

        verifyNoInteractions(checkoutFacade);
    }

    @Test
    void checkout_alreadyPaid_staysOnFormWithMessage() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 7L, false)).thenThrow(new DuplicateResourceException("already paid"));

        mockMvc.perform(post("/payments")
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "QR_MOCK"))
                .andExpect(view().name("payments/new"))
                .andExpect(model().attribute("error", "already paid"));
    }

    @Test
    void checkout_notOwner_staysOnFormWithThaiMessage() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 7L, false)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(post("/payments")
                        .param("payableType", "LAUNDRY_ORDER").param("payableId", "5").param("method", "QR_MOCK"))
                .andExpect(view().name("payments/new"))
                .andExpect(model().attribute("error", "คุณชำระได้เฉพาะรายการของตัวเองเท่านั้น"));
    }

    @Test
    void detail_owner_showsPayment() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        PaymentResponse payment = response(PaymentStatus.PAID);
        when(checkoutFacade.getPayment(3L, 7L, false)).thenReturn(payment);

        mockMvc.perform(get("/payments/3"))
                .andExpect(view().name("payments/detail"))
                .andExpect(model().attribute("payment", payment));
    }

    @Test
    void detail_notOwner_redirectsWithError() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        when(checkoutFacade.getPayment(3L, 7L, false)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(get("/payments/3"))
                .andExpect(view().name("redirect:/payments/new"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void detail_nonNumericId_isNotFound() throws Exception {
        loginAs(7L, Role.CUSTOMER);

        mockMvc.perform(get("/payments/abc")).andExpect(status().isNotFound());
        verifyNoInteractions(checkoutFacade);
    }

    @Test
    void staffList_filtersByStatus() throws Exception {
        loginAs(2L, Role.STAFF);
        Payment pending = Payment.forPayable(payable(), PaymentMethod.CASH);
        when(paymentService.findAll(eq(PaymentStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending)));

        mockMvc.perform(get("/staff/payments").param("status", "PENDING"))
                .andExpect(view().name("payments/list"))
                .andExpect(model().attributeExists("payments"))
                .andExpect(model().attribute("status", PaymentStatus.PENDING));
    }

    @Test
    void confirm_redirectsToDetailWithSuccessMessage() throws Exception {
        loginAs(2L, Role.STAFF);
        when(checkoutFacade.confirmPayment(3L)).thenReturn(response(PaymentStatus.PAID));

        mockMvc.perform(post("/staff/payments/3/confirm"))
                .andExpect(view().name("redirect:/payments/3"))
                .andExpect(flash().attribute("success", "ยืนยันการรับเงินเรียบร้อยแล้ว"));
        verify(checkoutFacade).confirmPayment(3L);
    }

    @Test
    void confirm_unknownPayment_redirectsToStaffList() throws Exception {
        loginAs(2L, Role.STAFF);
        when(checkoutFacade.confirmPayment(3L)).thenThrow(new ResourceNotFoundException("Payment 3 not found"));

        mockMvc.perform(post("/staff/payments/3/confirm"))
                .andExpect(view().name("redirect:/staff/payments"))
                .andExpect(flash().attribute("error", "Payment 3 not found"));
    }

    @Test
    void confirm_alreadyPaid_redirectsToDetailWithError() throws Exception {
        loginAs(2L, Role.STAFF);
        when(checkoutFacade.confirmPayment(3L)).thenThrow(new DuplicateResourceException("Payment 3 is already paid"));

        mockMvc.perform(post("/staff/payments/3/confirm"))
                .andExpect(view().name("redirect:/payments/3"))
                .andExpect(flash().attribute("error", "รายการนี้ชำระแล้ว"));
    }

    private static void loginAs(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPassword("x");
        user.setRole(role);
        AppUserDetails principal = new AppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(3L, PayableType.LAUNDRY_ORDER, 5L, new BigDecimal("120.00"),
                PaymentMethod.QR_MOCK, status, null, LocalDateTime.of(2026, 10, 10, 10, 0));
    }

    private static Payable payable() {
        return new Payable() {
            public Long getId() { return 5L; }
            public BigDecimal getPayableAmount() { return new BigDecimal("120.00"); }
            public PayableType getPayableType() { return PayableType.LAUNDRY_ORDER; }
            public Long getOwnerUserId() { return 7L; }
        };
    }
}
