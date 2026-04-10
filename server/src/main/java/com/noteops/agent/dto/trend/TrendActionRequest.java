package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendActionRequest(
    String action,
    @JsonProperty("operator_note")
    String operatorNote
) {
}
