package com.noteops.agent.dto.preference;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpsertUserPreferenceProfileRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("interest_profile")
    InterestProfilePayload interestProfile
) {
}
