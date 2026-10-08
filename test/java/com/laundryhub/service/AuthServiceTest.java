package com.laundryhub.service;

import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.RegisterRequest;
import com.laundryhub.dto.response.UserResponse;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.mapper.UserMapper;
import com.laundryhub.repository.UserRepository;
import com.laundryhub.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private final RegisterRequest request =
            new RegisterRequest("somchai", "somchai@test.com", "password123", "Somchai Test", "0899999999", "Khon Kaen");

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, new UserMapper());
    }

    @Test
    void register_success_encodesPasswordAndCreatesCustomerWithProfile() {
        when(userRepository.existsByUsername("somchai")).thenReturn(false);
        when(userRepository.existsByEmail("somchai@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getPassword()).isEqualTo("ENCODED").isNotEqualTo("password123");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getProfile()).isNotNull();
        assertThat(user.getProfile().getFullName()).isEqualTo("Somchai Test");
        assertThat(user.getProfile().getUser()).isSameAs(user);

        assertThat(response.username()).isEqualTo("somchai");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void register_duplicateUsername_throwsAndSavesNothing() {
        when(userRepository.existsByUsername("somchai")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("somchai");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsAndSavesNothing() {
        when(userRepository.existsByUsername("somchai")).thenReturn(false);
        when(userRepository.existsByEmail("somchai@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("somchai@test.com");

        verify(userRepository, never()).save(any());
    }
}
