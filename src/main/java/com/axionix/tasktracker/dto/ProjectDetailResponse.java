package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Project;

import java.time.Instant;
import java.util.List;

/** One project including its members, as returned by GET /projects/{id}. */
public record ProjectDetailResponse(Long id, String name, String description, Long ownerId,
                                    Instant createdAt, List<UserResponse> members) {

    public static ProjectDetailResponse from(Project project, List<UserResponse> members) {
        return new ProjectDetailResponse(project.getId(), project.getName(), project.getDescription(),
                project.getOwner().getId(), project.getCreatedAt(), members);
    }
}
