package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.TaskStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.Instant;

/**
 * Partial update (PATCH): a field that is missing from the JSON is left unchanged.
 * <p>
 * due_date and assignee_id use JsonNullable so we can tell apart
 * "not sent" (leave as is) from an explicit null (clear the value).
 */
public record UpdateTaskRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        Priority priority,

        TaskStatus status,

        JsonNullable<Instant> dueDate,

        JsonNullable<Long> assigneeId
) {

    /** True when due_date was present in the JSON (possibly as an explicit null). */
    public boolean hasDueDate() {
        return dueDate != null && dueDate.isPresent();
    }

    /** True when assignee_id was present in the JSON (possibly as an explicit null). */
    public boolean hasAssignee() {
        return assigneeId != null && assigneeId.isPresent();
    }
}
