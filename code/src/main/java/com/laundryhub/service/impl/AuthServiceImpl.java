package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.CustomerProfile;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.RegisterRequest;
import com.laundryhub.dto.response.UserResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.mapper.UserMapper;
import com.laundryhub.repository.UserRepository;
import com.laundryhub.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    // One transaction: if saving the profile fails, the user row is rolled back too (no orphan accounts).
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);

        CustomerProfile profile = new CustomerProfile();
        profile.setFullName(request.fullName());
        profile.setPhone(request.phone());
        profile.setAddress(request.address());
        user.attachProfile(profile);

        // cascade = ALL on User.profile persists the profile in the same save
        return userMapper.toResponse(userRepository.save(user));
    }
}
