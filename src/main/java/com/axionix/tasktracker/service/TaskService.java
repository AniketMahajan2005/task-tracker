package com.axionix.tasktracker.service;

import com.axionix.tasktracker.dto.CreateTaskRequest;
import com.axionix.tasktracker.dto.PageResponse;
import com.axionix.tasktracker.dto.TaskResponse;
import com.axionix.tasktracker.dto.TaskSummaryResponse;
import com.axionix.tasktracker.dto.UpdateTaskRequest;
import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.Project;
import com.axionix.tasktracker.entity.Task;
import com.axionix.tasktracker.entity.TaskStatus;
import com.axionix.tasktracker.entity.User;
import com.axionix.tasktracker.exception.ApiException;
import com.axionix.tasktracker.exception.ErrorCode;
import com.axionix.tasktracker.repository.TaskRepository;
import com.axionix.tasktracker.repository.TaskSpecifications;
import com.axionix.tasktracker.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TaskService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService access;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, ProjectAccessService access) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    /** Filters accepted by the list endpoint. Every field is optional. */
    public record TaskFilter(TaskStatus status, Priority priority, Long assigneeId,
                             Instant dueFrom, Instant dueTo) {
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public TaskResponse create(Long userId, Long projectId, CreateTaskRequest request) {
        Project project = access.requireMember(projectId, userId);
        User creator = userRepository.getReferenceById(userId);

        requireDueDateNotInPast(request.dueDate());
        User assignee = resolveAssignee(projectId, request.assigneeId());

        Task task = new Task(
                project,
                request.title().trim(),
                blankToNull(request.description()),
                request.priority() == null ? Priority.MEDIUM : request.priority(),
                request.dueDate(),
                assignee,
                creator);
        return TaskResponse.from(taskRepository.save(task));
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, Long projectId, TaskFilter filter,
                                           int page, int size, String sortBy, String order) {
        access.requireMember(projectId, userId);

        if (page < 1) {
            throw ApiException.validation("page must be 1 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.validation("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (filter.dueFrom() != null && filter.dueTo() != null && filter.dueFrom().isAfter(filter.dueTo())) {
            throw ApiException.validation("due_from must not be after due_to");
        }

        Specification<Task> spec = TaskSpecifications.inProject(projectId);
        if (filter.status() != null) {
            spec = spec.and(TaskSpecifications.hasStatus(filter.status()));
        }
        if (filter.priority() != null) {
            spec = spec.and(TaskSpecifications.hasPriority(filter.priority()));
        }
        if (filter.assigneeId() != null) {
            spec = spec.and(TaskSpecifications.assignedTo(filter.assigneeId()));
        }
        if (filter.dueFrom() != null) {
            spec = spec.and(TaskSpecifications.dueOnOrAfter(filter.dueFrom()));
        }
        if (filter.dueTo() != null) {
            spec = spec.and(TaskSpecifications.dueOnOrBefore(filter.dueTo()));
        }

        // id is the tie-breaker so pages never overlap or skip rows when many tasks share a sort value.
        Sort sort = Sort.by(parseDirection(order), parseSortProperty(sortBy)).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<Task> result = taskRepository.findAll(spec, PageRequest.of(page - 1, size, sort));
        return PageResponse.from(result, TaskResponse::from);
    }

    // ---------------------------------------------------------------- update

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest request) {
        Task task = findTask(taskId);
        Long projectId = task.getProject().getId();
        access.requireMember(projectId, userId);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (request.title() != null) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(blankToNull(request.description()));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.status() != null) {
            task.changeStatus(request.status(), now);
        }
        if (request.hasDueDate()) {
            Instant dueDate = request.dueDate().get();   // null means "clear the due date"
            requireDueDateNotInPast(dueDate);
            task.setDueDate(dueDate);
        }
        if (request.hasAssignee()) {
            task.setAssignee(resolveAssignee(projectId, request.assigneeId().get()));   // null means "unassign"
        }

        task.setUpdatedAt(now);
        return TaskResponse.from(taskRepository.save(task));
    }

    // ---------------------------------------------------------------- delete

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = findTask(taskId);
        Project project = access.requireMember(task.getProject().getId(), userId);
        if (!project.isOwnedBy(userId) && !task.isCreatedBy(userId)) {
            throw ApiException.forbidden("Only the project owner or the task creator can delete this task");
        }
        taskRepository.delete(task);
    }

    // --------------------------------------------------------------- summary

    @Transactional(readOnly = true)
    public TaskSummaryResponse summary(Long userId, Long projectId) {
        access.requireMember(projectId, userId);

        // Start every bucket at 0 so clients always get the full set of keys.
        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            byStatus.put(s, 0L);
        }
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            byPriority.put(p, 0L);
        }
        taskRepository.countByStatus(projectId).forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        taskRepository.countByPriority(projectId).forEach(row -> byPriority.put(row.getPriority(), row.getTotal()));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new TaskSummaryResponse(projectId, total, byStatus, byPriority);
    }

    // --------------------------------------------------------------- helpers

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("Task " + taskId + " not found"));
    }

    /** An assignee must be a member of the same project; null means "no assignee". */
    private User resolveAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!access.isMember(projectId, assigneeId)) {
            throw new ApiException(ErrorCode.INVALID_ASSIGNEE,
                    "Assignee " + assigneeId + " is not a member of this project");
        }
        return userRepository.getReferenceById(assigneeId);
    }

    /**
     * Design decision: past due dates are rejected whenever a due date is set or changed.
     * Existing overdue tasks can still be edited because an unchanged due date is not re-validated.
     */
    private void requireDueDateNotInPast(Instant dueDate) {
        if (dueDate != null && dueDate.isBefore(Instant.now())) {
            throw ApiException.validation("due_date must not be in the past");
        }
    }

    private static String parseSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "created_at", "createdat" -> "createdAt";
            case "due_date", "duedate" -> "dueDate";
            default -> throw ApiException.validation("sort_by must be one of: created_at, due_date");
        };
    }

    private static Sort.Direction parseDirection(String order) {
        if (order == null || order.isBlank()) {
            return Sort.Direction.DESC;
        }
        return switch (order.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw ApiException.validation("order must be one of: asc, desc");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
