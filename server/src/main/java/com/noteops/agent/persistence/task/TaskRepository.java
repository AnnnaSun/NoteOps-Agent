package com.noteops.agent.persistence.task;

import com.noteops.agent.application.task.TaskApplicationService;
import com.noteops.agent.domain.task.TaskRelatedEntityType;
import com.noteops.agent.domain.task.TaskSource;
import com.noteops.agent.domain.task.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    TaskApplicationService.TaskView create(UUID userId,
                                           UUID noteId,
                                           TaskSource taskSource,
                                           String taskType,
                                           String title,
                                           String description,
                                           TaskStatus status,
                                           int priority,
                                           Instant dueAt,
                                           TaskRelatedEntityType relatedEntityType,
                                           UUID relatedEntityId);

    List<TaskApplicationService.TaskView> findTodayByUserId(UUID userId, Instant dueAtInclusive);

    Optional<TaskApplicationService.TaskView> findByIdAndUserId(UUID taskId, UUID userId);

    Optional<TaskApplicationService.TaskView> findOpenByUserIdAndSourceAndTaskTypeAndNoteId(UUID userId,
                                                                                             TaskSource taskSource,
                                                                                             String taskType,
                                                                                             UUID noteId);

    void updateStatus(UUID taskId, TaskStatus status);

    void refreshOpenTask(UUID taskId,
                         String title,
                         String description,
                         int priority,
                         Instant dueAt,
                         TaskRelatedEntityType relatedEntityType,
                         UUID relatedEntityId);
}
