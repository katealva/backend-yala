package com.yala.dto.user;

import com.yala.model.Role;

public record ResponseUserDTO(
        Long id,
        String name,
        String email,
        String avatarUrl,
        Float reputation,
        Boolean isVerifiedSeller,
        Role role) {
}
