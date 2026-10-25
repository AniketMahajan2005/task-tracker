package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * priority is optional and defaults to MEDIUM. A new task always starts as TODO.
 * due_date must be a full ISO-8601 date-time, e.g. 2030-01-31T10:00:00Z.
 */
public record CreateTaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        Priority priority,

        Instant dueDate,

        Long assigneeId
) {
}
