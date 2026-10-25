package com.axionix.tasktracker.controller;

import com.axionix.tasktracker.dto.CreateTaskRequest;
import com.axionix.tasktracker.dto.PageResponse;
import com.axionix.tasktracker.dto.TaskResponse;
import com.axionix.tasktracker.dto.TaskSummaryResponse;
import com.axionix.tasktracker.dto.UpdateTaskRequest;
import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.TaskStatus;
import com.axionix.tasktracker.security.AuthenticatedUser;
import com.axionix.tasktracker.service.TaskService;
import com.axionix.tasktracker.service.TaskService.TaskFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task in a project (members only)")
    public TaskResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long projectId,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(user.id(), projectId, request);
    }

    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "List tasks with pagination, filters and sorting (members only)")
    public PageResponse<TaskResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(name = "assignee_id", required = false) Long assigneeId,
            @Parameter(description = "Inclusive lower bound, ISO-8601 date-time, e.g. 2030-01-01T00:00:00Z")
            @RequestParam(name = "due_from", required = false) Instant dueFrom,
            @Parameter(description = "Inclusive upper bound, ISO-8601 date-time, e.g. 2030-12-31T23:59:59Z")
            @RequestParam(name = "due_to", required = false) Instant dueTo,
            @Parameter(description = "created_at (default) or due_date")
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @Parameter(description = "asc or desc (default desc)")
            @RequestParam(required = false) String order,
            @Parameter(description = "1-based page number")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size, default 10, maximum 50")
            @RequestParam(defaultValue = "" + TaskService.DEFAULT_PAGE_SIZE) int size) {
        TaskFilter filter = new TaskFilter(status, priority, assigneeId, dueFrom, dueTo);
        return taskService.list(user.id(), projectId, filter, page, size, sortBy, order);
    }

    @GetMapping("/projects/{projectId}/summary")
    @Operation(summary = "Task counts grouped by status and priority (members only)")
    public TaskSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long projectId) {
        return taskService.summary(user.id(), projectId);
    }

    @PatchMapping("/tasks/{taskId}")
    @Operation(summary = "Partially update a task (members only)")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(user.id(), taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (project owner or task creator only)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId) {
        taskService.delete(user.id(), taskId);
    }
}
