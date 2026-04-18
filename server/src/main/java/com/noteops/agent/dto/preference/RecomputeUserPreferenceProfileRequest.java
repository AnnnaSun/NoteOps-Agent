package com.noteops.agent.dto.preference;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecomputeUserPreferenceProfileRequest(
    @JsonProperty("user_id")
    String userId
) {
}
