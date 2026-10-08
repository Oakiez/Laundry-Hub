package com.laundryhub.controller.api;

import com.laundryhub.dto.request.UpdateProfileRequest;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.dto.response.UserResponse;
import com.laundryhub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserApiController {

    // "owner or admin": #id is the path variable, principal.id is the logged-in user's id (AppUserDetails.getId())
    private static final String OWNER_OR_ADMIN = "hasRole('ADMIN') or #id == authentication.principal.id";

    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Get a user (owner or admin)")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Get a customer profile (owner or admin)")
    public ProfileResponse getProfile(@PathVariable Long id) {
        return userService.getProfile(id);
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Update a customer profile (owner or admin)")
    public ProfileResponse updateProfile(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(id, request);
    }
}
