package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.ServiceTypeApiController;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.response.ServiceTypeResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.service.ServiceTypeService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Service type endpoints: any logged-in user can read the price list, only ADMIN can change it. */
@WebMvcTest(controllers = ServiceTypeApiController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ServiceTypeSecurityTest {

    private static final String BODY = "{\"name\":\"Dry clean\",\"pricePerKg\":50,\"expressSurcharge\":15}";

    @Autowired
    private MockMvc mockMvc;

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

    private final AppUserDetails admin = principal(1, Role.ADMIN);
    private final AppUserDetails customer = principal(3, Role.CUSTOMER);

    private static ServiceTypeResponse response() {
        return new ServiceTypeResponse(4L, "Dry clean", new BigDecimal("50.00"), new BigDecimal("15.00"), true);
    }

    @Test
    void list_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/service-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_customerCanReadPriceList() throws Exception {
        when(serviceTypeService.findActive()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/service-types").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Dry clean"));
    }

    @Test
    void create_customer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/service-types").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(serviceTypeService, never()).create(any());
    }

    @Test
    void create_admin_returns201() throws Exception {
        when(serviceTypeService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/service-types").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void create_negativePrice_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/service-types").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"pricePerKg\":-1,\"expressSurcharge\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pricePerKg"));
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        when(serviceTypeService.create(any())).thenThrow(new DuplicateResourceException("Service type 'Dry clean' already exists"));

        mockMvc.perform(post("/api/v1/service-types").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivate_customer403_admin204() throws Exception {
        mockMvc.perform(delete("/api/v1/service-types/4").with(user(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/service-types/4").with(user(admin)))
                .andExpect(status().isNoContent());
        verify(serviceTypeService).deactivate(4L);
    }
}
