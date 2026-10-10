package com.laundryhub.controller.web;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.security.ApiAccessDeniedHandler;
import com.laundryhub.security.ApiAuthenticationEntryPoint;
import com.laundryhub.security.ApiErrorWriter;
import com.laundryhub.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The sign-up form asks for the password twice; the REST API does not need the second box. */
@WebMvcTest(AuthWebController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class AuthWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerWith(String confirm) {
        return post("/register").with(csrf())
                .param("username", "somchai").param("email", "s@test.com")
                .param("password", "password123").param("fullName", "Somchai")
                .param("confirmPassword", confirm);
    }

    @Test
    void register_withDifferentConfirmPassword_showsThaiErrorAndDoesNotRegister() throws Exception {
        mockMvc.perform(registerWith("password124"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString("รหัสผ่านทั้งสองช่องไม่ตรงกัน")));

        verify(authService, never()).register(any());
    }

    @Test
    void register_withMatchingConfirmPassword_registersAndRedirectsToLogin() throws Exception {
        mockMvc.perform(registerWith("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(authService).register(any());
    }

    @Test
    void register_withoutConfirmPassword_isRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "somchai").param("email", "s@test.com")
                        .param("password", "password123").param("fullName", "Somchai"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("รหัสผ่านทั้งสองช่องไม่ตรงกัน")));

        verify(authService, never()).register(any());
    }
}
