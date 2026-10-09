package com.laundryhub.dto.response;

import com.laundryhub.domain.enums.Role;

/** Deliberately has no password field. */
public record UserResponse(Long id, String username, String email, Role role, boolean enabled) {
}
