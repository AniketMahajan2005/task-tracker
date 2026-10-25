package com.axionix.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email must not be blank")
        String email,

        @NotBlank(message = "password must not be blank")
        String password
) {
}
