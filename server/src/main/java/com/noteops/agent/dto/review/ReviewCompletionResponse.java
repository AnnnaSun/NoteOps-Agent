package com.noteops.agent.dto.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.review.ReviewApplicationService;

import java.math.BigDecimal;
import java.time.Instant;

public record ReviewCompletionResponse(
    String id,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("queue_type")
    String queueType,
    @JsonProperty("completion_status")
    String completionStatus,
    @JsonProperty("completion_reason")
    String completionReason,
    @JsonProperty("self_recall_result")
    String selfRecallResult,
    @JsonProperty("note")
    String note,
    @JsonProperty("next_review_at")
    Instant nextReviewAt,
    @JsonProperty("retry_after_hours")
    int retryAfterHours,
    @JsonProperty("unfinished_count")
    int unfinishedCount,
    @JsonProperty("mastery_score")
    BigDecimal masteryScore
) {

    public static ReviewCompletionResponse from(ReviewApplicationService.ReviewCompletionView view) {
        return new ReviewCompletionResponse(
            view.id().toString(),
            view.noteId().toString(),
            view.queueType().name(),
            view.completionStatus().name(),
            view.completionReason() == null ? null : view.completionReason().name(),
            view.selfRecallResult() == null ? null : view.selfRecallResult().name(),
            view.note(),
            view.nextReviewAt(),
            view.retryAfterHours(),
            view.unfinishedCount(),
            view.masteryScore()
        );
    }
}
