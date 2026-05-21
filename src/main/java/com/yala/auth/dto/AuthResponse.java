package com.yala.auth.dto;

import com.yala.user.Role;
import com.yala.user.User;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String email,
        String name,
        Role role) {

    public static AuthResponse of(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole());
    }
}
