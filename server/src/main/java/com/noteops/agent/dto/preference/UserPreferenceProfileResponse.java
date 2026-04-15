package com.noteops.agent.dto.preference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.preference.UserPreferenceProfileApplicationService;

import java.time.Instant;

public record UserPreferenceProfileResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("interest_profile")
    InterestProfilePayload interestProfile,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static UserPreferenceProfileResponse from(UserPreferenceProfileApplicationService.UserPreferenceProfileView view) {
        return new UserPreferenceProfileResponse(
            view.id().toString(),
            view.userId().toString(),
            InterestProfilePayload.from(view.interestProfile()),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
