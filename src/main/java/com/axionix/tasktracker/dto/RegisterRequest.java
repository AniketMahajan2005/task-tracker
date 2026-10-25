package com.axionix.tasktracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        // 72 is BCrypt's input limit; longer passwords would be silently truncated.
        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password
) {
}
