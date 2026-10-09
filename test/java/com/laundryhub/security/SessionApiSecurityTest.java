package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.SessionApiController;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.response.SessionResponse;
import com.laundryhub.exception.*;
import com.laundryhub.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionApiController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class SessionApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SessionService service;
    private static final String BODY = """
            {"userId":3,"startTime":"2030-01-01T10:00:00","durationMinutes":30}
            """;
    static AppUserDetails principal(long id, Role role) {
        User u = new User(); u.setId(id); u.setUsername("user" + id); u.setPassword("HASH"); u.setRole(role);
        return new AppUserDetails(u);
    }
    private final AppUserDetails customer = principal(3, Role.CUSTOMER);
    private final AppUserDetails staff = principal(2, Role.STAFF);
    static SessionResponse response(SessionStatus state) {
        var start = LocalDateTime.of(2030, 1, 1, 10, 0);
        return new SessionResponse(7L, 5L, 3L, state, start, start.plusMinutes(30), 30,
                new BigDecimal("65.00"), start.minusDays(1));
    }

    @Test void anonymousCannotBook() throws Exception {
        mvc.perform(post("/api/v1/machines/5/sessions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test void customerBookingPassesTrustedActorAndReturnsServerPrice() throws Exception {
        when(service.book(eq(5L), eq(3L), eq(false), any())).thenReturn(response(SessionStatus.RESERVED));
        mvc.perform(post("/api/v1/machines/5/sessions").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(65))
                .andExpect(jsonPath("$.endTime").value("2030-01-01T10:30:00"));
        verify(service).book(eq(5L), eq(3L), eq(false), any());
    }

    @Test void staffCanBookForCustomerAndUsesStaffPrincipal() throws Exception {
        when(service.book(eq(5L), eq(2L), eq(true), any())).thenReturn(response(SessionStatus.RESERVED));
        mvc.perform(post("/api/v1/machines/5/sessions").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        verify(service).book(eq(5L), eq(2L), eq(true), any());
    }

    @ParameterizedTest @ValueSource(ints = {9, 181})
    void invalidDurationNeverCallsService(int minutes) throws Exception {
        mvc.perform(post("/api/v1/machines/5/sessions").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace(":30", ":" + minutes)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("durationMinutes"));
        verifyNoInteractions(service);
    }

    @Test void overlapReturns409AndWrongOwnerReturns403() throws Exception {
        when(service.book(eq(5L), eq(3L), eq(false), any())).thenThrow(new BookingConflictException("Overlap"));
        mvc.perform(post("/api/v1/machines/5/sessions").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        when(service.start(7L, 3L, false)).thenThrow(new AccessDeniedException("Another owner"));
        mvc.perform(patch("/api/v1/sessions/7/start").with(user(customer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test void lifecycleRoutesUseAuthenticatedIdentity() throws Exception {
        when(service.start(7L, 3L, false)).thenReturn(response(SessionStatus.IN_USE));
        when(service.finish(7L, 2L, true)).thenReturn(response(SessionStatus.COMPLETED));
        when(service.cancel(8L, 3L, false)).thenReturn(response(SessionStatus.CANCELLED));
        mvc.perform(patch("/api/v1/sessions/7/start?currentUserId=2&staff=true").with(user(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_USE"));
        mvc.perform(patch("/api/v1/sessions/7/finish").with(user(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(patch("/api/v1/sessions/8/cancel").with(user(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(service).start(7L, 3L, false);
    }

    @Test void ownerHistoryIsPagedAndOtherUserCannotReachService() throws Exception {
        when(service.findForUser(eq(3L), eq(3L), eq(false), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/users/3/sessions").with(user(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/v1/users/9/sessions").with(user(customer))).andExpect(status().isForbidden());
        verify(service, never()).findForUser(eq(9L), any(), anyBoolean(), any());
    }

    @Test void onlyStaffReadsMachineHistory() throws Exception {
        when(service.findForMachine(eq(5L), eq(true), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/machines/5/sessions").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/machines/5/sessions").with(user(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verify(service, times(1)).findForMachine(eq(5L), eq(true), any());
    }

    @Test void missingSessionReturns404AndInvalidStateUsesShared400Contract() throws Exception {
        when(service.findById(99L, 3L, false)).thenThrow(new ResourceNotFoundException("Session not found"));
        when(service.finish(7L, 3L, false)).thenThrow(new BusinessRuleException("Session must be IN_USE"));
        mvc.perform(get("/api/v1/sessions/99").with(user(customer))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/sessions/7/finish").with(user(customer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
