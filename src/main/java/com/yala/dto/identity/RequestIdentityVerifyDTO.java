package com.yala.dto.identity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RequestIdentityVerifyDTO(

        @NotNull
        @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 dígitos")
        String personalNumber,

        String firstName,

        String lastName) {
}
