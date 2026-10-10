package com.laundryhub.controller.web;

import com.laundryhub.domain.entity.Notification;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Controller-level test of the notification page (standalone MockMvc, no template rendering). */
@ExtendWith(MockitoExtension.class)
class NotificationWebControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noRender = (model, request, response) -> { };
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationWebController(notificationService, new NotificationMapper()))
                .setViewResolvers((viewName, locale) -> noRender)
                .build();
        loginAs(7L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_showsOnlyCurrentUsersNotifications_allByDefault() throws Exception {
        when(notificationService.findByUser(eq(7L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new Notification(7L, "ชำระเงินสำเร็จ"))));

        mockMvc.perform(get("/notifications"))
                .andExpect(view().name("notifications/list"))
                .andExpect(model().attributeExists("notifications"))
                .andExpect(model().attribute("unread", false));
    }

    @Test
    void list_unreadFilter_passesTrue() throws Exception {
        when(notificationService.findByUser(eq(7L), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/notifications").param("unread", "true"))
                .andExpect(view().name("notifications/list"))
                .andExpect(model().attribute("unread", true));
    }

    @Test
    void markRead_marksForCurrentUser_andReturnsToList() throws Exception {
        mockMvc.perform(post("/notifications/4/read"))
                .andExpect(view().name("redirect:/notifications"));

        verify(notificationService).markRead(4L, 7L);
    }

    @Test
    void markRead_keepsUnreadFilter() throws Exception {
        mockMvc.perform(post("/notifications/4/read").param("unread", "true"))
                .andExpect(view().name("redirect:/notifications?unread=true"));
    }

    @Test
    void markRead_notOwner_showsError() throws Exception {
        when(notificationService.markRead(4L, 7L)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(post("/notifications/4/read"))
                .andExpect(view().name("redirect:/notifications"))
                .andExpect(flash().attribute("error", "คุณอ่านได้เฉพาะแจ้งเตือนของตัวเองเท่านั้น"));
    }

    @Test
    void markRead_unknownId_showsError() throws Exception {
        when(notificationService.markRead(4L, 7L)).thenThrow(new ResourceNotFoundException("Notification 4 not found"));

        mockMvc.perform(post("/notifications/4/read"))
                .andExpect(flash().attribute("error", "Notification 4 not found"));
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
