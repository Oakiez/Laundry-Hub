package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.CustomerProfile;
import com.laundryhub.dto.request.UpdateProfileRequest;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.dto.response.UserResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.UserMapper;
import com.laundryhub.repository.CustomerProfileRepository;
import com.laundryhub.repository.UserRepository;
import com.laundryhub.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, CustomerProfileRepository profileRepository,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    @Override
    public ProfileResponse getProfile(Long userId) {
        return userMapper.toProfileResponse(findProfile(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        CustomerProfile profile = findProfile(userId);
        profile.setFullName(request.fullName());
        profile.setPhone(request.phone());
        profile.setAddress(request.address());
        // no explicit save(): the entity is managed, so JPA writes the change when the transaction commits
        return userMapper.toProfileResponse(profile);
    }

    // Only CUSTOMER accounts have a profile (1:1), so STAFF/ADMIN ids end up here as "not found"
    private CustomerProfile findProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile of user " + userId + " not found"));
    }
}
