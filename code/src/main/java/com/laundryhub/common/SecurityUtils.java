package com.laundryhub.common;

import com.laundryhub.domain.enums.Role;
import com.laundryhub.security.AppUserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    public static Role currentRole() {
        return currentUser().getRole();
    }

    private static AppUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return user;
    }
}
