package com.noteops.agent.dto.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.review.ReviewApplicationService;

import java.util.List;

public record ReviewFeedbackResponse(
    @JsonProperty("review_item_id")
    String reviewItemId,
    @JsonProperty("recall_feedback_summary")
    String recallFeedbackSummary,
    @JsonProperty("next_review_hint")
    String nextReviewHint,
    @JsonProperty("extension_suggestions")
    List<String> extensionSuggestions,
    @JsonProperty("follow_up_task_suggestion")
    String followUpTaskSuggestion
) {

    public static ReviewFeedbackResponse from(ReviewApplicationService.ReviewFeedbackView view) {
        return new ReviewFeedbackResponse(
            view.reviewItemId().toString(),
            view.recallFeedbackSummary(),
            view.nextReviewHint(),
            view.extensionSuggestions(),
            view.followUpTaskSuggestion()
        );
    }
}
