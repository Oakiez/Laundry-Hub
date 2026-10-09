package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.AuthApiController;
import com.laundryhub.controller.api.BranchApiController;
import com.laundryhub.controller.api.UserApiController;
import com.laundryhub.controller.web.BranchWebController;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.response.BranchResponse;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.dto.response.UserResponse;
import com.laundryhub.exception.GlobalExceptionHandler;
import com.laundryhub.service.AuthService;
import com.laundryhub.service.BranchService;
import com.laundryhub.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks who may call what. Services are mocked, so only the security rules and the
 * error format (401/403 must use the standard ApiErrorResponse) are under test.
 */
@WebMvcTest(controllers = {AuthApiController.class, UserApiController.class,
        BranchApiController.class, BranchWebController.class})
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private BranchService branchService;

    private static final String BRANCH_JSON = "{\"name\":\"New Branch\",\"address\":\"addr\",\"phone\":\"043\"}";

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

    // ---------- 401: not logged in ----------

    @Test
    void api_withoutLogin_returns401WithStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/branches"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/v1/branches"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void api_withWrongPassword_returns401WithStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/branches").with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void webPage_withoutLogin_redirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/admin/branches").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    // ---------- public endpoint ----------

    @Test
    void register_isPublic() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(9L, "somchai", "s@test.com", Role.CUSTOMER, true));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"somchai\",\"email\":\"s@test.com\",\"password\":\"password123\",\"fullName\":\"Som\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_withInvalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"bad\",\"password\":\"1\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    // ---------- branches: read = any logged-in user, write = ADMIN ----------

    @Test
    void branches_customerCanRead() throws Exception {
        when(branchService.findAll()).thenReturn(List.of(new BranchResponse(1L, "A", "addr", "043")));

        mockMvc.perform(get("/api/v1/branches").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"));
    }

    @Test
    void branches_customerCannotCreate_returns403WithStandardErrorBody() throws Exception {
        mockMvc.perform(post("/api/v1/branches").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(BRANCH_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/api/v1/branches"));
    }

    @Test
    void branches_adminCanCreate() throws Exception {
        when(branchService.create(any())).thenReturn(new BranchResponse(5L, "New Branch", "addr", "043"));

        mockMvc.perform(post("/api/v1/branches").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BRANCH_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    // ---------- users: owner or ADMIN ----------

    @Test
    void user_ownerCanReadOwnData() throws Exception {
        when(userService.getById(3L)).thenReturn(new UserResponse(3L, "user3", "c@test.com", Role.CUSTOMER, true));

        mockMvc.perform(get("/api/v1/users/3").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void user_customerCannotReadSomeoneElse() throws Exception {
        mockMvc.perform(get("/api/v1/users/1").with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void user_adminCanReadAnyProfile() throws Exception {
        when(userService.getProfile(3L)).thenReturn(new ProfileResponse(3L, "Customer", "081", "addr"));

        mockMvc.perform(get("/api/v1/users/3/profile").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Customer"));
    }

    @Test
    void profile_customerCannotUpdateSomeoneElse() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/1/profile")
                        .with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- web area /admin/** = ADMIN only ----------

    @Test
    void adminPage_customerGets403() throws Exception {
        mockMvc.perform(get("/admin/branches").with(user(customer)))
                .andExpect(status().isForbidden());
    }
}
