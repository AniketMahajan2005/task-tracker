package com.axionix.tasktracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        String email
) {
}
