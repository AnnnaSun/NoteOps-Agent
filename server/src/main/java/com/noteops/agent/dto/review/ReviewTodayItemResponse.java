package com.noteops.agent.dto.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.review.ReviewApplicationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReviewTodayItemResponse(
    String id,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("queue_type")
    String queueType,
    @JsonProperty("completion_status")
    String completionStatus,
    @JsonProperty("completion_reason")
    String completionReason,
    @JsonProperty("mastery_score")
    BigDecimal masteryScore,
    @JsonProperty("next_review_at")
    Instant nextReviewAt,
    @JsonProperty("retry_after_hours")
    int retryAfterHours,
    @JsonProperty("unfinished_count")
    int unfinishedCount,
    String title,
    @JsonProperty("current_summary")
    String currentSummary,
    @JsonProperty("current_key_points")
    List<String> currentKeyPoints,
    @JsonProperty("ai_recall_summary")
    String aiRecallSummary,
    @JsonProperty("ai_review_key_points")
    List<String> aiReviewKeyPoints,
    @JsonProperty("ai_extension_preview")
    String aiExtensionPreview
) {

    public static ReviewTodayItemResponse from(ReviewApplicationService.ReviewTodayItemView view) {
        return new ReviewTodayItemResponse(
            view.id().toString(),
            view.noteId().toString(),
            view.queueType().name(),
            view.completionStatus().name(),
            view.completionReason() == null ? null : view.completionReason().name(),
            view.masteryScore(),
            view.nextReviewAt(),
            view.retryAfterHours(),
            view.unfinishedCount(),
            view.title(),
            view.currentSummary(),
            view.currentKeyPoints(),
            view.aiRecallSummary(),
            view.aiReviewKeyPoints(),
            view.aiExtensionPreview()
        );
    }
}
