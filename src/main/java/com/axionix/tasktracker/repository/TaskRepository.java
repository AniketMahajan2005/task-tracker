package com.axionix.tasktracker.repository;

import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.Task;
import com.axionix.tasktracker.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Row shape for the summary endpoint: one row per status that has tasks. */
    interface StatusCount {
        TaskStatus getStatus();

        long getTotal();
    }

    /** Row shape for the summary endpoint: one row per priority that has tasks. */
    interface PriorityCount {
        Priority getPriority();

        long getTotal();
    }

    @Query("select t.status as status, count(t) as total from Task t where t.project.id = :projectId group by t.status")
    List<StatusCount> countByStatus(@Param("projectId") Long projectId);

    @Query("select t.priority as priority, count(t) as total from Task t where t.project.id = :projectId group by t.priority")
    List<PriorityCount> countByPriority(@Param("projectId") Long projectId);
}
