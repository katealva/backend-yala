package com.yala.user.dto;

import com.yala.user.Role;

public record UserResponse(
        Long id,
        String name,
        String email,
        String avatarUrl,
        Float reputation,
        Boolean isVerifiedSeller,
        Role role) {
}
