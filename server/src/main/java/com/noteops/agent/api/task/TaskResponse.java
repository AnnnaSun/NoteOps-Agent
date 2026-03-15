package com.noteops.agent.api.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.task.TaskApplicationService;

import java.time.Instant;

public record TaskResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("task_source")
    String taskSource,
    @JsonProperty("task_type")
    String taskType,
    String title,
    String description,
    String status,
    int priority,
    @JsonProperty("due_at")
    Instant dueAt,
    @JsonProperty("related_entity_type")
    String relatedEntityType,
    @JsonProperty("related_entity_id")
    String relatedEntityId,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static TaskResponse from(TaskApplicationService.TaskView view) {
        return new TaskResponse(
            view.id().toString(),
            view.userId().toString(),
            view.noteId() == null ? null : view.noteId().toString(),
            view.taskSource().name(),
            view.taskType(),
            view.title(),
            view.description(),
            view.status().name(),
            view.priority(),
            view.dueAt(),
            view.relatedEntityType().name(),
            view.relatedEntityId() == null ? null : view.relatedEntityId().toString(),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
