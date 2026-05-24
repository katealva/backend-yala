package com.yala.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.auth.dto.AuthResponse;
import com.yala.auth.dto.LoginRequest;
import com.yala.auth.dto.RefreshTokenRequest;
import com.yala.auth.dto.RegisterRequest;
import com.yala.exceptions.EmailAlreadyExistsException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.security.JwtService;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserWhenEmailIsUnique() {
        RegisterRequest request = new RegisterRequest(
                "Ada Lovelace", "ada@yala.pe", "password123", Role.USER);
        when(userRepository.existsByEmail("ada@yala.pe")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.email()).isEqualTo("ada@yala.pe");
        assertThat(response.role()).isEqualTo(Role.USER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowEmailAlreadyExistsExceptionWhenEmailIsDuplicated() {
        RegisterRequest request = new RegisterRequest(
                "Ada Lovelace", "ada@yala.pe", "password123", Role.USER);
        when(userRepository.existsByEmail("ada@yala.pe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnTokenWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("ada@yala.pe", "password123");
        User user = User.builder()
                .id(1L)
                .name("Ada Lovelace")
                .email("ada@yala.pe")
                .passwordHash("hashed-password")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenPasswordIsWrong() {
        LoginRequest request = new LoginRequest("ada@yala.pe", "wrong-password");
        User user = User.builder()
                .id(1L)
                .email("ada@yala.pe")
                .passwordHash("hashed-password")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest("ghost@yala.pe", "password123");
        when(userRepository.findByEmail("ghost@yala.pe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldReturnNewAccessTokenWhenRefreshTokenIsValid() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        User user = User.builder()
                .id(1L)
                .email("ada@yala.pe")
                .role(Role.USER)
                .build();
        when(jwtService.isTokenValid("valid-refresh-token")).thenReturn(true);
        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("ada@yala.pe");
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");

        AuthResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("valid-refresh-token");
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenRefreshTokenIsInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");
        when(jwtService.isTokenValid("invalid-refresh-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class);
    }
}
