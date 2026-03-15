package com.noteops.agent.api.task;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateTaskStatusRequest(
    @JsonProperty("user_id")
    String userId
) {
}
