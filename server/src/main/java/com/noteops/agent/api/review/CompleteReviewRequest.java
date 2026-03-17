package com.noteops.agent.api.review;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompleteReviewRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("completion_status")
    String completionStatus,
    @JsonProperty("completion_reason")
    String completionReason,
    @JsonProperty("self_recall_result")
    String selfRecallResult,
    @JsonProperty("note")
    String note
) {
}
