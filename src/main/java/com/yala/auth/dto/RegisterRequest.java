package com.yala.auth.dto;

import com.yala.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull @Size(min = 2, max = 100) String name,
        @NotNull @Email String email,
        @NotNull @Size(min = 8) String password,
        Role role) {

    /** Defaults the role to {@link Role#USER} when the client omits it. */
    public RegisterRequest {
        if (role == null) {
            role = Role.USER;
        }
    }
}
