package com.laundryhub.dto.response;

public record ProfileResponse(Long userId, String fullName, String phone, String address) {
}
