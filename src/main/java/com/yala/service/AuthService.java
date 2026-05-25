package com.yala.service;

import com.yala.dto.auth.ResponseAuthDTO;
import com.yala.dto.auth.RequestLoginDTO;
import com.yala.dto.auth.RequestRefreshTokenDTO;
import com.yala.dto.auth.RequestRegisterDTO;
import com.yala.exceptions.EmailAlreadyExistsException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.security.JwtService;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles user registration and login, issuing JWT access and refresh tokens. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public ResponseAuthDTO register(RequestRegisterDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(
                    "Email already registered: " + request.email());
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .reputation(0.0f)
                .isVerifiedSeller(false)
                .build();
        return buildAuthResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public ResponseAuthDTO login(RequestLoginDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public ResponseAuthDTO refreshToken(RequestRefreshTokenDTO request) {
        String token = request.refreshToken();
        if (!jwtService.isTokenValid(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid or expired refresh token"));
        String accessToken = jwtService.generateAccessToken(user);
        return ResponseAuthDTO.of(user, accessToken, token);
    }

    private ResponseAuthDTO buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return ResponseAuthDTO.of(user, accessToken, refreshToken);
    }
}
