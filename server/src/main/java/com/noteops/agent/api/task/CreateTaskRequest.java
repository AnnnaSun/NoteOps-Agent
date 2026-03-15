package com.noteops.agent.api.task;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTaskRequest(
    @JsonProperty("user_id")
    String userId,
    String title,
    String description,
    @JsonProperty("task_type")
    String taskType,
    Integer priority,
    @JsonProperty("due_at")
    String dueAt,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("related_entity_type")
    String relatedEntityType,
    @JsonProperty("related_entity_id")
    String relatedEntityId
) {
}
