package com.laundryhub.mapper;

import com.laundryhub.domain.entity.CustomerProfile;
import com.laundryhub.domain.entity.User;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.dto.response.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isEnabled());
    }

    public ProfileResponse toProfileResponse(CustomerProfile profile) {
        return new ProfileResponse(profile.getUser().getId(), profile.getFullName(),
                profile.getPhone(), profile.getAddress());
    }
}
