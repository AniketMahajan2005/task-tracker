package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Project;

import java.time.Instant;

/** Project as returned by create and list. */
public record ProjectResponse(Long id, String name, String description, Long ownerId, Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getOwner().getId(), project.getCreatedAt());
    }
}
