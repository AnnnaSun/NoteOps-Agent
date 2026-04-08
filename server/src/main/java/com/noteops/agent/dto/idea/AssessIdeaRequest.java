package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssessIdeaRequest(
    @JsonProperty("user_id")
    String userId
) {
}
