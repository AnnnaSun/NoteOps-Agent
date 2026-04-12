package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendPlanTriggerRequest(
    @JsonProperty("user_id")
    String userId
) {
}
