package com.laundryhub.service;

import com.laundryhub.domain.entity.CustomerProfile;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.UpdateProfileRequest;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.UserMapper;
import com.laundryhub.repository.CustomerProfileRepository;
import com.laundryhub.repository.UserRepository;
import com.laundryhub.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerProfileRepository profileRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, profileRepository, new UserMapper());
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("customer1");
        u.setEmail("c1@test.com");
        u.setPassword("HASH");
        u.setRole(Role.CUSTOMER);
        return u;
    }

    private CustomerProfile profileOf(User user) {
        CustomerProfile p = new CustomerProfile();
        p.setUser(user);
        p.setFullName("Old Name");
        p.setPhone("081");
        p.setAddress("Old address");
        return p;
    }

    @Test
    void getById_found_returnsResponseWithoutPassword() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L)));

        var response = userService.getById(3L);

        assertThat(response.username()).isEqualTo("customer1");
        assertThat(response.toString()).doesNotContain("HASH");
    }

    @Test
    void getById_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfile_notFound_throws() {
        when(profileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("2");
    }

    @Test
    void updateProfile_changesAllFields() {
        CustomerProfile profile = profileOf(user(3L));
        when(profileRepository.findByUserId(3L)).thenReturn(Optional.of(profile));

        ProfileResponse response = userService.updateProfile(3L, new UpdateProfileRequest("New Name", "082", "New address"));

        assertThat(profile.getFullName()).isEqualTo("New Name");
        assertThat(profile.getPhone()).isEqualTo("082");
        assertThat(profile.getAddress()).isEqualTo("New address");
        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.fullName()).isEqualTo("New Name");
    }

    @Test
    void updateProfile_notFound_throws() {
        when(profileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(2L, new UpdateProfileRequest("N", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
