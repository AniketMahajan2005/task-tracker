package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.Task;
import com.axionix.tasktracker.entity.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        Long id,
        Long projectId,
        String title,
        String description,
        Priority priority,
        TaskStatus status,
        Instant dueDate,
        Long assigneeId,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getDueDate(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getCreatedBy().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt());
    }
}
