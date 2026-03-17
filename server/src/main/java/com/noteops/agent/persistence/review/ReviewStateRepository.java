package com.noteops.agent.persistence.review;

import com.noteops.agent.application.review.ReviewApplicationService;
import com.noteops.agent.domain.review.ReviewCompletionReason;
import com.noteops.agent.domain.review.ReviewCompletionStatus;
import com.noteops.agent.domain.review.ReviewQueueType;
import com.noteops.agent.domain.review.ReviewSelfRecallResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewStateRepository {

    void createInitialScheduleIfMissing(UUID userId, UUID noteId, Instant now);

    List<ReviewApplicationService.ReviewStateView> findDueByUserId(UUID userId, Instant now);

    List<ReviewApplicationService.ReviewStateView> findUpcomingByUserId(UUID userId, Instant nextReviewAfterExclusive);

    Optional<ReviewApplicationService.ReviewStateView> findByIdAndUserId(UUID reviewStateId, UUID userId);

    Optional<ReviewApplicationService.ReviewStateView> findByUserIdAndNoteIdAndQueueType(UUID userId, UUID noteId, ReviewQueueType queueType);

    ReviewApplicationService.ReviewStateView create(UUID userId,
                                                    UUID noteId,
                                                    ReviewQueueType queueType,
                                                    ReviewCompletionStatus completionStatus,
                                                    ReviewCompletionReason completionReason,
                                                    ReviewSelfRecallResult selfRecallResult,
                                                    String note,
                                                    BigDecimal masteryScore,
                                                    Instant lastReviewedAt,
                                                    Instant nextReviewAt,
                                                    int unfinishedCount,
                                                    int retryAfterHours);

    void update(UUID reviewStateId,
                ReviewCompletionStatus completionStatus,
                ReviewCompletionReason completionReason,
                ReviewSelfRecallResult selfRecallResult,
                String note,
                BigDecimal masteryScore,
                Instant lastReviewedAt,
                Instant nextReviewAt,
                int unfinishedCount,
                int retryAfterHours);
}
