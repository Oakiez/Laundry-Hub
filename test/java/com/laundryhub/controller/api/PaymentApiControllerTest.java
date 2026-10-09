package com.laundryhub.controller.api;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.exception.GlobalExceptionHandler;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ทดสอบ controller แบบ standalone: ตรวจ status, รูปแบบ JSON และการส่งต่อ "ผู้ใช้ปัจจุบัน/สิทธิ์พนักงาน" ให้ Facade
 * (@PreAuthorize ไม่ทำงานใน standalone จึงไม่ได้ทดสอบที่นี่ — ต้องใช้ integration test)
 */
@ExtendWith(MockitoExtension.class)
class PaymentApiControllerTest {

    private static final String BODY =
            "{\"payableType\":\"LAUNDRY_ORDER\",\"payableId\":5,\"method\":\"QR_MOCK\"}";

    @Mock
    private CheckoutFacade checkoutFacade;
    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentApiController(checkoutFacade, paymentService, new PaymentMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkout_asCustomer_returns201_andPassesCurrentUserWithStaffFalse() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 7L, false)).thenReturn(response(PaymentStatus.PAID));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payableType").value("LAUNDRY_ORDER"));
    }

    @Test
    void checkout_asStaff_passesStaffTrue() throws Exception {
        loginAs(2L, Role.STAFF);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 2L, true)).thenReturn(response(PaymentStatus.PAID));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void checkout_missingFields_returns400_withFieldErrors() throws Exception {
        loginAs(7L, Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void checkout_notOwner_returns403() throws Exception {
        loginAs(99L, Role.CUSTOMER);
        CheckoutRequest request = new CheckoutRequest(PayableType.LAUNDRY_ORDER, 5L, PaymentMethod.QR_MOCK);
        when(checkoutFacade.checkout(request, 99L, false)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPayment_delegatesWithCurrentUser() throws Exception {
        loginAs(7L, Role.CUSTOMER);
        when(checkoutFacade.getPayment(3L, 7L, false)).thenReturn(response(PaymentStatus.PENDING));

        mockMvc.perform(get("/api/v1/payments/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void list_returnsPagedContent() throws Exception {
        loginAs(2L, Role.STAFF);
        Payment payment = Payment.forPayable(payable(), PaymentMethod.CASH);
        when(paymentService.findAll(eq(PaymentStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(payment)));

        mockMvc.perform(get("/api/v1/payments").param("status", "PENDING").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].amount").value(120.00))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void list_unknownSortField_returns400_notFiveHundred() throws Exception {
        loginAs(2L, Role.STAFF);

        mockMvc.perform(get("/api/v1/payments").param("sort", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("abc")));
        verifyNoInteractions(paymentService);
    }

    @Test
    void list_allowedSortField_isAccepted() throws Exception {
        loginAs(2L, Role.STAFF);
        when(paymentService.findAll(eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/payments").param("sort", "amount,asc"))
                .andExpect(status().isOk());
    }

    @Test
    void list_invalidStatus_returns400() throws Exception {
        loginAs(2L, Role.STAFF);

        mockMvc.perform(get("/api/v1/payments").param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_returns200_andDelegates() throws Exception {
        loginAs(2L, Role.STAFF);
        when(checkoutFacade.confirmPayment(3L)).thenReturn(response(PaymentStatus.PAID));

        mockMvc.perform(patch("/api/v1/payments/3/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        verify(checkoutFacade).confirmPayment(3L);
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
        return new PaymentResponse(1L, PayableType.LAUNDRY_ORDER, 5L, new BigDecimal("120.00"),
                PaymentMethod.QR_MOCK, status, null, LocalDateTime.of(2026, 10, 9, 10, 0));
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
