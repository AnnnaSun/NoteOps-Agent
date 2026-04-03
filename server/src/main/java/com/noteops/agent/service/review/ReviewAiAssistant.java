package com.noteops.agent.service.review;

import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.review.ReviewSelfRecallResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReviewAiAssistant {

    ReviewRenderResult renderTodayItems(ReviewRenderRequest request);

    ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request);

    record ReviewRenderRequest(
        UUID userId,
        UUID traceId,
        List<ReviewRenderCandidate> items
    ) {
        public ReviewRenderRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ReviewRenderCandidate(
        UUID reviewItemId,
        UUID noteId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        List<String> currentTags,
        ReviewQueueType queueType,
        Instant nextReviewAt,
        int retryAfterHours
    ) {
        public ReviewRenderCandidate {
            currentKeyPoints = currentKeyPoints == null ? List.of() : List.copyOf(currentKeyPoints);
            currentTags = currentTags == null ? List.of() : List.copyOf(currentTags);
        }
    }

    record RenderedReviewItem(
        UUID reviewItemId,
        String recallSummary,
        List<String> reviewKeyPoints,
        String extensionPreview
    ) {
        public RenderedReviewItem {
            reviewKeyPoints = reviewKeyPoints == null ? List.of() : List.copyOf(reviewKeyPoints);
        }
    }

    record ReviewRenderResult(
        Map<UUID, RenderedReviewItem> itemsByReviewItemId
    ) {
        public ReviewRenderResult {
            itemsByReviewItemId = itemsByReviewItemId == null ? Map.of() : Map.copyOf(itemsByReviewItemId);
        }
    }

    record ReviewFeedbackRequest(
        UUID userId,
        UUID traceId,
        UUID reviewItemId,
        UUID noteId,
        String noteTitle,
        String currentSummary,
        List<String> currentKeyPoints,
        List<String> currentTags,
        ReviewQueueType queueType,
        ReviewCompletionStatus completionStatus,
        ReviewCompletionReason completionReason,
        ReviewSelfRecallResult selfRecallResult,
        String recallNote
    ) {
        public ReviewFeedbackRequest {
            currentKeyPoints = currentKeyPoints == null ? List.of() : List.copyOf(currentKeyPoints);
            currentTags = currentTags == null ? List.of() : List.copyOf(currentTags);
        }
    }

    record ReviewFeedbackResult(
        String recallFeedbackSummary,
        String nextReviewHint,
        List<String> extensionSuggestions,
        String followUpTaskSuggestion
    ) {
        public ReviewFeedbackResult {
            extensionSuggestions = extensionSuggestions == null ? List.of() : List.copyOf(extensionSuggestions);
        }
    }
}
