package com.noteops.agent.application.review;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.domain.review.ReviewCompletionReason;
import com.noteops.agent.domain.review.ReviewCompletionStatus;
import com.noteops.agent.domain.review.ReviewQueueType;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.review.ReviewStateRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewApplicationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReviewStateRepository reviewStateRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final Clock clock;

    @Autowired
    public ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                                    NoteRepository noteRepository,
                                    AgentTraceRepository agentTraceRepository,
                                    UserActionEventRepository userActionEventRepository) {
        this(reviewStateRepository, noteRepository, agentTraceRepository, userActionEventRepository, Clock.systemUTC());
    }

    ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                             NoteRepository noteRepository,
                             AgentTraceRepository agentTraceRepository,
                             UserActionEventRepository userActionEventRepository,
                             Clock clock) {
        this.reviewStateRepository = reviewStateRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.clock = clock;
    }

    public List<ReviewTodayItemView> listToday(String userIdRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        Instant now = Instant.now(clock);

        List<NoteQueryService.NoteSummaryView> notes = noteRepository.findAllByUserId(userId);
        for (NoteQueryService.NoteSummaryView note : notes) {
            reviewStateRepository.createInitialScheduleIfMissing(userId, note.id(), now);
        }

        Map<UUID, NoteQueryService.NoteSummaryView> noteById = notes.stream()
            .collect(Collectors.toMap(NoteQueryService.NoteSummaryView::id, Function.identity()));

        return reviewStateRepository.findDueByUserId(userId, now).stream()
            .map(reviewState -> toTodayItem(reviewState, noteById))
            .toList();
    }

    public ReviewCompletionView complete(String reviewItemIdRaw, CompleteReviewCommand command) {
        UUID reviewItemId = parseUuid(reviewItemIdRaw, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        ReviewCompletionStatus completionStatus = parseStatus(command.completionStatus());
        ReviewCompletionReason completionReason = validateReason(completionStatus, command.completionReason());

        ReviewStateView target = reviewStateRepository.findByIdAndUserId(reviewItemId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_ITEM_NOT_FOUND", "review item not found"));

        Instant now = Instant.now(clock);
        if (target.queueType() == ReviewQueueType.SCHEDULE) {
            applyScheduleCompletion(target, completionStatus, completionReason, now);
        } else {
            applyRecallCompletion(target, completionStatus, completionReason, now);
        }

        UUID traceId = agentTraceRepository.create(
            userId,
            "REVIEW_COMPLETE",
            "Complete review " + reviewItemId,
            "REVIEW_STATE",
            reviewItemId,
            List.of("review-worker"),
            Map.of(
                "review_item_id", reviewItemId,
                "queue_type", target.queueType().name(),
                "completion_status", completionStatus.name()
            )
        );

        userActionEventRepository.append(
            userId,
            "REVIEW_COMPLETED",
            "REVIEW_STATE",
            reviewItemId,
            traceId,
            completionPayload(target.noteId(), completionStatus, completionReason)
        );

        agentTraceRepository.markCompleted(
            traceId,
            "Completed review " + reviewItemId,
            Map.of(
                "review_item_id", reviewItemId,
                "note_id", target.noteId(),
                "completion_status", completionStatus.name()
            )
        );

        ReviewStateView updated = reviewStateRepository.findByIdAndUserId(reviewItemId, userId).orElseThrow();
        return toCompletionView(updated);
    }

    private void applyScheduleCompletion(ReviewStateView target,
                                         ReviewCompletionStatus completionStatus,
                                         ReviewCompletionReason completionReason,
                                         Instant now) {
        switch (completionStatus) {
            case COMPLETED -> reviewStateRepository.update(
                target.id(),
                ReviewCompletionStatus.COMPLETED,
                null,
                increase(target.masteryScore(), 20),
                now,
                now.plus(3, ChronoUnit.DAYS),
                0,
                0
            );
            case PARTIAL -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.PARTIAL,
                    completionReason,
                    decrease(target.masteryScore(), 10),
                    now,
                    now.plus(3, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, completionReason, now,
                    target.masteryScore(), target.unfinishedCount() + 1, 24, now.plus(24, ChronoUnit.HOURS));
            }
            case NOT_STARTED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.NOT_STARTED,
                    completionReason,
                    target.masteryScore(),
                    now,
                    now.plus(1, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.NOT_STARTED, completionReason, now,
                    target.masteryScore(), target.unfinishedCount() + 1, 4, now.plus(4, ChronoUnit.HOURS));
            }
            case ABANDONED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.ABANDONED,
                    completionReason,
                    decrease(target.masteryScore(), 15),
                    now,
                    now.plus(2, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.ABANDONED, completionReason, now,
                    target.masteryScore(), target.unfinishedCount() + 1, 48, now.plus(48, ChronoUnit.HOURS));
            }
        }
    }

    private void applyRecallCompletion(ReviewStateView target,
                                       ReviewCompletionStatus completionStatus,
                                       ReviewCompletionReason completionReason,
                                       Instant now) {
        switch (completionStatus) {
            case COMPLETED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.COMPLETED,
                    null,
                    increase(target.masteryScore(), 10),
                    now,
                    now.plus(7, ChronoUnit.DAYS),
                    0,
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.COMPLETED, null, now,
                    target.masteryScore(), 0, 0, now.plus(3, ChronoUnit.DAYS));
            }
            case PARTIAL -> reviewStateRepository.update(
                target.id(),
                ReviewCompletionStatus.PARTIAL,
                completionReason,
                target.masteryScore(),
                now,
                now.plus(24, ChronoUnit.HOURS),
                target.unfinishedCount() + 1,
                24
            );
            case NOT_STARTED -> reviewStateRepository.update(
                target.id(),
                ReviewCompletionStatus.NOT_STARTED,
                completionReason,
                target.masteryScore(),
                now,
                now.plus(4, ChronoUnit.HOURS),
                target.unfinishedCount() + 1,
                4
            );
            case ABANDONED -> reviewStateRepository.update(
                target.id(),
                ReviewCompletionStatus.ABANDONED,
                completionReason,
                target.masteryScore(),
                now,
                now.plus(48, ChronoUnit.HOURS),
                target.unfinishedCount() + 1,
                48
            );
        }
    }

    private void upsertLinkedQueue(ReviewStateView target,
                                   ReviewQueueType queueType,
                                   ReviewCompletionStatus completionStatus,
                                   ReviewCompletionReason completionReason,
                                   Instant lastReviewedAt,
                                   BigDecimal masteryScore,
                                   int unfinishedCount,
                                   int retryAfterHours,
                                   Instant nextReviewAt) {
        reviewStateRepository.findByUserIdAndNoteIdAndQueueType(target.userId(), target.noteId(), queueType)
            .ifPresentOrElse(
                existing -> reviewStateRepository.update(
                    existing.id(),
                    completionStatus,
                    completionReason,
                    existing.masteryScore(),
                    lastReviewedAt,
                    nextReviewAt,
                    unfinishedCount,
                    retryAfterHours
                ),
                () -> reviewStateRepository.create(
                    target.userId(),
                    target.noteId(),
                    queueType,
                    completionStatus,
                    completionReason,
                    masteryScore,
                    lastReviewedAt,
                    nextReviewAt,
                    unfinishedCount,
                    retryAfterHours
                )
            );
    }

    private ReviewTodayItemView toTodayItem(ReviewStateView reviewState, Map<UUID, NoteQueryService.NoteSummaryView> noteById) {
        NoteQueryService.NoteSummaryView note = noteById.get(reviewState.noteId());
        if (note == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REVIEW_NOTE_MISSING", "review note is missing");
        }
        return new ReviewTodayItemView(
            reviewState.id(),
            reviewState.noteId(),
            reviewState.queueType(),
            reviewState.completionStatus(),
            reviewState.completionReason(),
            reviewState.masteryScore(),
            reviewState.nextReviewAt(),
            reviewState.retryAfterHours(),
            reviewState.unfinishedCount(),
            note.title(),
            note.currentSummary(),
            note.currentKeyPoints()
        );
    }

    private ReviewCompletionView toCompletionView(ReviewStateView reviewState) {
        return new ReviewCompletionView(
            reviewState.id(),
            reviewState.noteId(),
            reviewState.queueType(),
            reviewState.completionStatus(),
            reviewState.completionReason(),
            reviewState.nextReviewAt(),
            reviewState.retryAfterHours(),
            reviewState.unfinishedCount(),
            reviewState.masteryScore()
        );
    }

    private Map<String, Object> completionPayload(UUID noteId,
                                                  ReviewCompletionStatus completionStatus,
                                                  ReviewCompletionReason completionReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("note_id", noteId);
        payload.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            payload.put("completion_reason", completionReason.name());
        }
        return payload;
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private ReviewCompletionStatus parseStatus(String rawValue) {
        try {
            return ReviewCompletionStatus.valueOf(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPLETION_STATUS", "completion_status is invalid");
        }
    }

    private ReviewCompletionReason validateReason(ReviewCompletionStatus completionStatus, String rawReason) {
        if (completionStatus == ReviewCompletionStatus.COMPLETED) {
            return null;
        }
        if (rawReason == null || rawReason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_COMPLETION_REASON", "completion_reason is required");
        }
        try {
            return ReviewCompletionReason.valueOf(rawReason);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPLETION_REASON", "completion_reason is invalid");
        }
    }

    private BigDecimal increase(BigDecimal current, int delta) {
        return current.add(BigDecimal.valueOf(delta)).min(HUNDRED);
    }

    private BigDecimal decrease(BigDecimal current, int delta) {
        BigDecimal updated = current.subtract(BigDecimal.valueOf(delta));
        return updated.max(BigDecimal.ZERO);
    }

    public record CompleteReviewCommand(String userId, String completionStatus, String completionReason) {
    }

    public record ReviewStateView(
        UUID id,
        UUID userId,
        UUID noteId,
        ReviewQueueType queueType,
        BigDecimal masteryScore,
        Instant lastReviewedAt,
        Instant nextReviewAt,
        ReviewCompletionStatus completionStatus,
        ReviewCompletionReason completionReason,
        int unfinishedCount,
        int retryAfterHours,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ReviewTodayItemView(
        UUID id,
        UUID noteId,
        ReviewQueueType queueType,
        ReviewCompletionStatus completionStatus,
        ReviewCompletionReason completionReason,
        BigDecimal masteryScore,
        Instant nextReviewAt,
        int retryAfterHours,
        int unfinishedCount,
        String title,
        String currentSummary,
        List<String> currentKeyPoints
    ) {
    }

    public record ReviewCompletionView(
        UUID id,
        UUID noteId,
        ReviewQueueType queueType,
        ReviewCompletionStatus completionStatus,
        ReviewCompletionReason completionReason,
        Instant nextReviewAt,
        int retryAfterHours,
        int unfinishedCount,
        BigDecimal masteryScore
    ) {
    }
}
