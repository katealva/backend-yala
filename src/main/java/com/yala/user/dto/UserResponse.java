package com.yala.user.dto;

import com.yala.user.Role;
import com.yala.user.User;

public record UserResponse(
        Long id,
        String name,
        String email,
        String avatarUrl,
        Float reputation,
        Boolean isVerifiedSeller,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getReputation(),
                user.getIsVerifiedSeller(),
                user.getRole());
    }
}
