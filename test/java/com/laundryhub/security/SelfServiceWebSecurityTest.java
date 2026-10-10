package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.web.SelfServiceWebController;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.response.*;
import com.laundryhub.exception.*;
import com.laundryhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SelfServiceWebController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class SelfServiceWebSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MachineService machines;
    @MockitoBean SessionService sessions;
    @MockitoBean BranchService branches;
    @MockitoBean com.laundryhub.service.PaymentService payments;
    private final AppUserDetails customer = principal(Role.CUSTOMER);
    private final AppUserDetails staff = principal(Role.STAFF);
    private final MachineResponse machine = new MachineResponse(5L, 1L, "Washer A", MachineType.WASHER,
            MachineStatus.AVAILABLE, new BigDecimal("20.00"), new BigDecimal("1.50"));
    private final LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
    private SessionResponse session(SessionStatus status) {
        return new SessionResponse(7L, 5L, 3L, status, start, start.plusMinutes(30), 30,
                new BigDecimal("65.00"), start.minusDays(1));
    }
    private static AppUserDetails principal(Role role) {
        User user = new User(); user.setId(3L); user.setUsername("web-user"); user.setPassword("HASH"); user.setRole(role);
        return new AppUserDetails(user);
    }
    @BeforeEach void fixtures() {
        when(machines.findById(5L)).thenReturn(machine);
        when(machines.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(machine)));
        when(branches.findAll()).thenReturn(List.of(new BranchResponse(1L, "Central", "Address", "000")));
        when(sessions.findForUser(eq(3L), eq(3L), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(session(SessionStatus.RESERVED))));
        when(sessions.findForMachine(eq(5L), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(session(SessionStatus.IN_USE))));
    }
    @Test void customerBoardRendersSharedMenuAndBookingLink() throws Exception {
        mvc.perform(get("/machines").with(user(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Washer A")))
                .andExpect(content().string(containsString("/machines/5/book")))
                .andExpect(content().string(not(containsString("/staff/machines/5/status"))));
    }
    @Test void staffBoardRendersProtectedStatusFormAndCsrf() throws Exception {
        mvc.perform(get("/staff/machines").with(user(staff))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/staff/machines/5/status")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }
    @Test void customerCannotAccessStaffBoardOrChangeMachineStatus() throws Exception {
        mvc.perform(get("/staff/machines").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/machines/5/status").with(user(customer)).with(csrf()).param("status", "OUT_OF_SERVICE"))
                .andExpect(status().isForbidden());
        verify(machines, never()).changeStatus(any(), any());
    }
    @Test void bookingFormRendersInputsAndCsrf() throws Exception {
        mvc.perform(get("/machines/5/book").with(user(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("datetime-local")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }
    @Test void forgedOwnerAndPriceAreIgnoredInBooking() throws Exception {
        when(sessions.book(eq(5L), eq(3L), eq(false), any())).thenReturn(session(SessionStatus.RESERVED));
        mvc.perform(post("/machines/5/book").with(user(customer)).with(csrf())
                        .param("startTime", "2030-01-01T10:00").param("durationMinutes", "30")
                        .param("userId", "999").param("amount", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/sessions/history"));
        verify(sessions).book(5L, 3L, false, new BookSessionRequest(3L, start, 30));
    }
    @Test void missingCsrfCannotBookOrStart() throws Exception {
        mvc.perform(post("/machines/5/book").with(user(customer)).param("startTime", "2030-01-01T10:00")
                .param("durationMinutes", "30")).andExpect(status().isForbidden());
        mvc.perform(post("/sessions/7/start").with(user(customer))).andExpect(status().isForbidden());
        verify(sessions, never()).book(any(), any(), anyBoolean(), any());
        verify(sessions, never()).start(any(), any(), anyBoolean());
    }
    @Test void invalidDurationAndMalformedTimeRedisplayFormWithoutBooking() throws Exception {
        mvc.perform(post("/machines/5/book").with(user(customer)).with(csrf())
                .param("startTime", "2030-01-01T10:00").param("durationMinutes", "9"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "durationMinutes"));
        mvc.perform(post("/machines/5/book").with(user(customer)).with(csrf())
                .param("startTime", "broken").param("durationMinutes", "30"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "startTime"));
        verify(sessions, never()).book(any(), any(), anyBoolean(), any());
    }
    @Test void overlapPreservesInputsAndDisplaysUsefulMessage() throws Exception {
        when(sessions.book(any(), any(), anyBoolean(), any())).thenThrow(new BookingConflictException("Overlap"));
        mvc.perform(post("/machines/5/book").with(user(customer)).with(csrf())
                .param("startTime", "2030-01-01T10:00").param("durationMinutes", "30"))
                .andExpect(status().isOk()).andExpect(view().name("machines/book"))
                .andExpect(content().string(containsString("ช่วงเวลานี้มีผู้จองแล้ว")))
                .andExpect(content().string(containsString("2030-01-01T10:00")));
    }
    @Test void historyUsesPrincipalEvenIfUserIdQueryIsForged() throws Exception {
        mvc.perform(get("/sessions/history").param("userId", "999").with(user(customer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/sessions/7/start")))
                .andExpect(content().string(containsString("/sessions/7/cancel")))
                .andExpect(content().string(not(containsString("/sessions/7/finish"))));
        verify(sessions).findForUser(eq(3L), eq(3L), eq(false), any());
    }
    @Test void customerDetailDoesNotLoadOtherCustomersSessions() throws Exception {
        mvc.perform(get("/machines/5").with(user(customer))).andExpect(status().isOk());
        verify(sessions, never()).findForMachine(any(), anyBoolean(), any());
    }
    @Test void historyLinksToSessionCheckoutExceptCancelledSessions() throws Exception {
        for (SessionStatus sessionStatus : List.of(SessionStatus.RESERVED, SessionStatus.IN_USE, SessionStatus.COMPLETED)) {
            when(sessions.findForUser(eq(3L), eq(3L), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.of(session(sessionStatus))));
            mvc.perform(get("/sessions/history").with(user(customer)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/payments/new?type=USAGE_SESSION&amp;id=7")));
        }
        when(sessions.findForUser(eq(3L), eq(3L), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(session(SessionStatus.CANCELLED))));
        mvc.perform(get("/sessions/history").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("type=USAGE_SESSION"))));
    }
    @Test void staffDetailRendersActiveSessionActions() throws Exception {
        mvc.perform(get("/machines/5").with(user(staff))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/sessions/7/finish")))
                .andExpect(content().string(not(containsString("/sessions/7/start"))));
    }
    @Test void lifecyclePassesTrustedActorAndRejectsOtherOwner() throws Exception {
        mvc.perform(post("/sessions/7/start").with(user(customer)).with(csrf()))
                .andExpect(redirectedUrl("/sessions/history"));
        verify(sessions).start(7L, 3L, false);
        when(sessions.cancel(7L, 3L, false)).thenThrow(new AccessDeniedException("Other owner"));
        mvc.perform(post("/sessions/7/cancel").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void staffCanFinishUsingStaffAuthorityAndStatusConflictIsShownAsFlash() throws Exception {
        mvc.perform(post("/sessions/7/finish").with(user(staff)).with(csrf()))
                .andExpect(redirectedUrl("/staff/machines"));
        verify(sessions).finish(7L, 3L, true);
        when(machines.changeStatus(5L, MachineStatus.OUT_OF_SERVICE)).thenThrow(new BookingConflictException("In use"));
        mvc.perform(post("/staff/machines/5/status").with(user(staff)).with(csrf()).param("status", "OUT_OF_SERVICE"))
                .andExpect(redirectedUrl("/staff/machines")).andExpect(flash().attributeExists("error"));
    }
    @Test void historyShowsThaiStatusAndPayButtonWhenNotPaid() throws Exception {
        mvc.perform(get("/sessions/history").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("จองแล้ว")))
                .andExpect(content().string(not(containsString(">RESERVED<"))))
                .andExpect(content().string(containsString("/payments/new?type=USAGE_SESSION&amp;id=7")));
    }
    @Test void historyHidesPayButtonWhenSessionAlreadyHasPayment() throws Exception {
        when(payments.sessionIdsWithPayment(any())).thenReturn(java.util.Set.of(7L));
        mvc.perform(get("/sessions/history").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("มีการชำระเงินแล้ว")))
                .andExpect(content().string(not(containsString("/payments/new?type=USAGE_SESSION"))));
    }
    @Test void machineBoardShowsThaiStatusAndTypeIcon() throws Exception {
        mvc.perform(get("/machines").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ว่าง")))
                .andExpect(content().string(containsString("🧺")))
                .andExpect(content().string(not(containsString(">AVAILABLE<"))));
    }
}
