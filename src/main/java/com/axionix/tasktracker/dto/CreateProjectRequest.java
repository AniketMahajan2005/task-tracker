package com.axionix.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description
) {
}
