package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.Priority;
import com.axionix.tasktracker.entity.TaskStatus;

import java.util.Map;

/** Every status and priority is present in the maps, with 0 when there are no tasks. */
public record TaskSummaryResponse(Long projectId,
                                  long totalTasks,
                                  Map<TaskStatus, Long> byStatus,
                                  Map<Priority, Long> byPriority) {
}
