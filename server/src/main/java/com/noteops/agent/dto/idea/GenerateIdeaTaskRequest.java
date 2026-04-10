package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerateIdeaTaskRequest(
    @JsonProperty("user_id")
    String userId
) {
}
