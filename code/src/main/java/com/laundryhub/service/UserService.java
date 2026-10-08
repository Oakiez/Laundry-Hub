package com.laundryhub.service;

import com.laundryhub.dto.request.UpdateProfileRequest;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.dto.response.UserResponse;

public interface UserService {

    UserResponse getById(Long id);

    ProfileResponse getProfile(Long userId);

    ProfileResponse updateProfile(Long userId, UpdateProfileRequest request);
}
