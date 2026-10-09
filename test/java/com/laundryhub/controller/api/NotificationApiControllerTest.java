package com.laundryhub.controller.api;

import com.laundryhub.domain.entity.Notification;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.NotificationMapper;
import com.laundryhub.security.AppUserDetails;
import com.laundryhub.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationApiControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationApiController(notificationService, new NotificationMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_passesUserAndUnreadFilter_andReturnsPage() throws Exception {
        when(notificationService.findByUser(eq(7L), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(new Notification(7L, "ออเดอร์ #1 สถานะ: WASHING"))));

        mockMvc.perform(get("/api/v1/users/7/notifications").param("unread", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("ออเดอร์ #1 สถานะ: WASHING"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void list_withoutUnreadParam_passesNull() throws Exception {
        when(notificationService.findByUser(eq(7L), eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/users/7/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void markRead_usesCurrentUserAsRequester() throws Exception {
        loginAs(7L);
        Notification notification = new Notification(7L, "hi");
        notification.markRead();
        when(notificationService.markRead(3L, 7L)).thenReturn(notification);

        mockMvc.perform(patch("/api/v1/notifications/3/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markRead_notOwner_returns403() throws Exception {
        loginAs(99L);
        when(notificationService.markRead(3L, 99L)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(patch("/api/v1/notifications/3/read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void markRead_missing_returns404() throws Exception {
        loginAs(7L);
        when(notificationService.markRead(99L, 7L)).thenThrow(new ResourceNotFoundException("Notification 99 not found"));

        mockMvc.perform(patch("/api/v1/notifications/99/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification 99 not found"));
    }

    private static void loginAs(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPassword("x");
        user.setRole(Role.CUSTOMER);
        AppUserDetails principal = new AppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
