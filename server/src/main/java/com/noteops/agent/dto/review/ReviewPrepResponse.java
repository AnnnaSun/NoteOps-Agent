package com.noteops.agent.dto.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.review.ReviewApplicationService;

import java.util.List;

public record ReviewPrepResponse(
    @JsonProperty("review_item_id")
    String reviewItemId,
    @JsonProperty("ai_recall_summary")
    String aiRecallSummary,
    @JsonProperty("ai_review_key_points")
    List<String> aiReviewKeyPoints,
    @JsonProperty("ai_extension_preview")
    String aiExtensionPreview
) {

    public static ReviewPrepResponse from(ReviewApplicationService.ReviewPrepView view) {
        return new ReviewPrepResponse(
            view.reviewItemId().toString(),
            view.aiRecallSummary(),
            view.aiReviewKeyPoints(),
            view.aiExtensionPreview()
        );
    }
}
