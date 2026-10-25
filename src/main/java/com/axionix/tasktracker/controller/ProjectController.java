package com.axionix.tasktracker.controller;

import com.axionix.tasktracker.dto.AddMemberRequest;
import com.axionix.tasktracker.dto.CreateProjectRequest;
import com.axionix.tasktracker.dto.ProjectDetailResponse;
import com.axionix.tasktracker.dto.ProjectResponse;
import com.axionix.tasktracker.dto.UserResponse;
import com.axionix.tasktracker.security.AuthenticatedUser;
import com.axionix.tasktracker.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a project (the creator becomes owner and member)")
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "List the projects the logged-in user is a member of")
    public List<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return projectService.listForUser(user.id());
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "View one project and its members (members only)")
    public ProjectDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable Long projectId) {
        return projectService.get(user.id(), projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a registered user to the project by email (owner only)")
    public UserResponse addMember(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long projectId,
                                  @Valid @RequestBody AddMemberRequest request) {
        return projectService.addMember(user.id(), projectId, request.email());
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the project and all its tasks (owner only)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId) {
        projectService.delete(user.id(), projectId);
    }
}
