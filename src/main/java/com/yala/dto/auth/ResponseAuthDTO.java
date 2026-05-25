package com.yala.dto.auth;

import com.yala.model.Role;
import com.yala.model.User;

public record ResponseAuthDTO(
        String accessToken,
        String refreshToken,
        Long userId,
        String email,
        String name,
        Role role) {

    public static ResponseAuthDTO of(User user, String accessToken, String refreshToken) {
        return new ResponseAuthDTO(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole());
    }
}
