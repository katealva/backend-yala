package com.yala.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record RequestLoginDTO(
        @NotNull @Email String email,
        @NotNull String password) {
}
