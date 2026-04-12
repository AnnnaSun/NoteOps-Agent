package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendActionRequest(
    @JsonProperty("user_id")
    String userId,
    String action,
    @JsonProperty("operator_note")
    String operatorNote
) {
}
