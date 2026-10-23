package com.axionix.tasktracker.repository;

import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.Task;
import com.axionix.tasktracker.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Small composable query fragments used by the task list endpoint. */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> inProject(Long projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> assignedTo(Long assigneeId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    /** Inclusive lower bound on due_date. Tasks without a due date never match. */
    public static Specification<Task> dueOnOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("dueDate"), from);
    }

    /** Inclusive upper bound on due_date. Tasks without a due date never match. */
    public static Specification<Task> dueOnOrBefore(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.<Instant>get("dueDate"), to);
    }
}
