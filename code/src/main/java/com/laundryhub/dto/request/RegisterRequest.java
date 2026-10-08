package com.laundryhub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No role field on purpose: self-registration is always CUSTOMER, so a client can never pick its own role. */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email @Size(max = 100) String email,
        // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 255) String address) {
}
