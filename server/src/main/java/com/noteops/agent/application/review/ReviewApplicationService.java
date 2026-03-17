package com.noteops.agent.application.review;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.domain.review.ReviewCompletionReason;
import com.noteops.agent.domain.review.ReviewCompletionStatus;
import com.noteops.agent.domain.review.ReviewQueueType;
import com.noteops.agent.domain.review.ReviewSelfRecallResult;
import com.noteops.agent.domain.task.TaskRelatedEntityType;
import com.noteops.agent.domain.task.TaskSource;
import com.noteops.agent.domain.task.TaskStatus;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.review.ReviewStateRepository;
import com.noteops.agent.persistence.task.TaskRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewApplicationService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String REVIEW_FOLLOW_UP_TASK_TYPE = "REVIEW_FOLLOW_UP";

    private final ReviewStateRepository reviewStateRepository;
    private final NoteRepository noteRepository;
    private final TaskRepository taskRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final Clock clock;

    @Autowired
    public ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                                    NoteRepository noteRepository,
                                    TaskRepository taskRepository,
                                    AgentTraceRepository agentTraceRepository,
                                    UserActionEventRepository userActionEventRepository) {
        this(reviewStateRepository, noteRepository, taskRepository, agentTraceRepository, userActionEventRepository, Clock.systemUTC());
    }

    ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                             NoteRepository noteRepository,
                             TaskRepository taskRepository,
                             AgentTraceRepository agentTraceRepository,
                             UserActionEventRepository userActionEventRepository,
                             Clock clock) {
        this.reviewStateRepository = reviewStateRepository;
        this.noteRepository = noteRepository;
        this.taskRepository = taskRepository;
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

    public List<ReviewTodayItemView> listUpcoming(String userIdRaw, String timezoneOffsetRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        ZoneOffset timezoneOffset = parseTimezoneOffset(timezoneOffsetRaw);
        Instant now = Instant.now(clock);
        Instant afterExclusive = endOfDay(now, timezoneOffset);

        List<NoteQueryService.NoteSummaryView> notes = noteRepository.findAllByUserId(userId);
        for (NoteQueryService.NoteSummaryView note : notes) {
            reviewStateRepository.createInitialScheduleIfMissing(userId, note.id(), now);
        }

        Map<UUID, NoteQueryService.NoteSummaryView> noteById = notes.stream()
            .collect(Collectors.toMap(NoteQueryService.NoteSummaryView::id, Function.identity()));

        return reviewStateRepository.findUpcomingByUserId(userId, afterExclusive).stream()
            .map(reviewState -> toTodayItem(reviewState, noteById))
            .toList();
    }

    @Transactional
    public ReviewCompletionView complete(String reviewItemIdRaw, CompleteReviewCommand command) {
        UUID reviewItemId = parseUuid(reviewItemIdRaw, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        ReviewCompletionStatus completionStatus = parseStatus(command.completionStatus());
        ReviewCompletionReason completionReason = validateReason(completionStatus, command.completionReason());

        ReviewStateView target = reviewStateRepository.findByIdAndUserId(reviewItemId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_ITEM_NOT_FOUND", "review item not found"));
        RecallFeedback recallFeedback = validateRecallFeedback(target.queueType(), command.selfRecallResult(), command.note());
        log.info(
            "action=review_complete_start user_id={} review_item_id={} queue_type={} completion_status={} self_recall_result={} has_note={}",
            userId,
            reviewItemId,
            target.queueType(),
            completionStatus,
            recallFeedback.selfRecallResult() == null ? null : recallFeedback.selfRecallResult().name(),
            recallFeedback.note() != null
        );

        Instant now = Instant.now(clock);
        if (target.queueType() == ReviewQueueType.SCHEDULE) {
            applyScheduleCompletion(target, completionStatus, completionReason, now);
        } else {
            applyRecallCompletion(target, completionStatus, completionReason, recallFeedback, now);
        }

        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("review_item_id", reviewItemId);
        traceState.put("queue_type", target.queueType().name());
        traceState.put("completion_status", completionStatus.name());
        if (recallFeedback.selfRecallResult() != null) {
            traceState.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        traceState.put("has_note", recallFeedback.note() != null);

        UUID traceId = agentTraceRepository.create(
            userId,
            "REVIEW_COMPLETE",
            "Complete review " + reviewItemId,
            "REVIEW_STATE",
            reviewItemId,
            List.of("review-worker"),
            traceState
        );

        ReviewStateView updated = reviewStateRepository.findByIdAndUserId(reviewItemId, userId).orElseThrow();
        UUID followUpTaskId = syncReviewFollowUpTask(updated, completionStatus, completionReason, traceId);

        Map<String, Object> payload = completionPayload(target.noteId(), completionStatus, completionReason, recallFeedback);
        if (followUpTaskId != null) {
            payload.put("follow_up_task_id", followUpTaskId);
        }
        userActionEventRepository.append(
            userId,
            "REVIEW_COMPLETED",
            "REVIEW_STATE",
            reviewItemId,
            traceId,
            payload
        );

        Map<String, Object> completionState = new LinkedHashMap<>();
        completionState.put("review_item_id", reviewItemId);
        completionState.put("note_id", target.noteId());
        completionState.put("completion_status", completionStatus.name());
        if (recallFeedback.selfRecallResult() != null) {
            completionState.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        if (recallFeedback.note() != null) {
            completionState.put("note", recallFeedback.note());
        }
        if (followUpTaskId != null) {
            completionState.put("follow_up_task_id", followUpTaskId);
        }
        agentTraceRepository.markCompleted(traceId, "Completed review " + reviewItemId, completionState);
        log.info(
            "action=review_complete_success user_id={} review_item_id={} queue_type={} trace_id={} completion_status={} self_recall_result={} has_note={}",
            userId,
            reviewItemId,
            target.queueType(),
            traceId,
            completionStatus,
            recallFeedback.selfRecallResult() == null ? null : recallFeedback.selfRecallResult().name(),
            recallFeedback.note() != null
        );
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
                null,
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
                    null,
                    null,
                    decrease(target.masteryScore(), 10),
                    now,
                    now.plus(3, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, completionReason, now,
                    null, null, target.masteryScore(), target.unfinishedCount() + 1, 24, now.plus(24, ChronoUnit.HOURS));
            }
            case NOT_STARTED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.NOT_STARTED,
                    completionReason,
                    null,
                    null,
                    target.masteryScore(),
                    now,
                    now.plus(1, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.NOT_STARTED, completionReason, now,
                    null, null, target.masteryScore(), target.unfinishedCount() + 1, 4, now.plus(4, ChronoUnit.HOURS));
            }
            case ABANDONED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.ABANDONED,
                    completionReason,
                    null,
                    null,
                    decrease(target.masteryScore(), 15),
                    now,
                    now.plus(2, ChronoUnit.DAYS),
                    target.unfinishedCount(),
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.RECALL, ReviewCompletionStatus.ABANDONED, completionReason, now,
                    null, null, target.masteryScore(), target.unfinishedCount() + 1, 48, now.plus(48, ChronoUnit.HOURS));
            }
        }
    }

    private void applyRecallCompletion(ReviewStateView target,
                                       ReviewCompletionStatus completionStatus,
                                       ReviewCompletionReason completionReason,
                                       RecallFeedback recallFeedback,
                                       Instant now) {
        switch (completionStatus) {
            case COMPLETED -> {
                reviewStateRepository.update(
                    target.id(),
                    ReviewCompletionStatus.COMPLETED,
                    null,
                    recallFeedback.selfRecallResult(),
                    recallFeedback.note(),
                    increase(target.masteryScore(), 10),
                    now,
                    now.plus(7, ChronoUnit.DAYS),
                    0,
                    0
                );
                upsertLinkedQueue(target, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.COMPLETED, null, now,
                    null, null, target.masteryScore(), 0, 0, now.plus(3, ChronoUnit.DAYS));
            }
            case PARTIAL -> reviewStateRepository.update(
                target.id(),
                ReviewCompletionStatus.PARTIAL,
                completionReason,
                recallFeedback.selfRecallResult(),
                recallFeedback.note(),
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
                recallFeedback.selfRecallResult(),
                recallFeedback.note(),
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
                recallFeedback.selfRecallResult(),
                recallFeedback.note(),
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
                                   ReviewSelfRecallResult selfRecallResult,
                                   String note,
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
                    selfRecallResult,
                    note,
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
                    selfRecallResult,
                    note,
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
            reviewState.selfRecallResult(),
            reviewState.note(),
            reviewState.nextReviewAt(),
            reviewState.retryAfterHours(),
            reviewState.unfinishedCount(),
            reviewState.masteryScore()
        );
    }

    private Map<String, Object> completionPayload(UUID noteId,
                                                  ReviewCompletionStatus completionStatus,
                                                  ReviewCompletionReason completionReason,
                                                  RecallFeedback recallFeedback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("note_id", noteId);
        payload.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            payload.put("completion_reason", completionReason.name());
        }
        if (recallFeedback.selfRecallResult() != null) {
            payload.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        if (recallFeedback.note() != null) {
            payload.put("note", recallFeedback.note());
        }
        return payload;
    }

    private UUID syncReviewFollowUpTask(ReviewStateView reviewState,
                                        ReviewCompletionStatus completionStatus,
                                        ReviewCompletionReason completionReason,
                                        UUID traceId) {
        return taskRepository.findOpenByUserIdAndSourceAndTaskTypeAndNoteId(
                reviewState.userId(),
                TaskSource.SYSTEM,
                REVIEW_FOLLOW_UP_TASK_TYPE,
                reviewState.noteId()
            )
            .map(existing -> resolveOpenTask(reviewState, completionStatus, completionReason, existing, traceId))
            .orElseGet(() -> createFollowUpTask(reviewState, completionStatus, completionReason, traceId));
    }

    private UUID resolveOpenTask(ReviewStateView reviewState,
                                 ReviewCompletionStatus completionStatus,
                                 ReviewCompletionReason completionReason,
                                 com.noteops.agent.application.task.TaskApplicationService.TaskView existing,
                                 UUID traceId) {
        if (completionStatus == ReviewCompletionStatus.COMPLETED) {
            taskRepository.updateStatus(existing.id(), TaskStatus.DONE);
            userActionEventRepository.append(
                reviewState.userId(),
                "SYSTEM_TASK_COMPLETED_FROM_REVIEW",
                "TASK",
                existing.id(),
                traceId,
                Map.of(
                    "task_type", REVIEW_FOLLOW_UP_TASK_TYPE,
                    "review_item_id", reviewState.id()
                )
            );
            return existing.id();
        }

        FollowUpTaskSpec spec = buildFollowUpTaskSpec(reviewState, completionStatus, completionReason);
        taskRepository.refreshOpenTask(
            existing.id(),
            spec.title(),
            spec.description(),
            spec.priority(),
            spec.dueAt(),
            TaskRelatedEntityType.REVIEW,
            reviewState.id()
        );
        userActionEventRepository.append(
            reviewState.userId(),
            "SYSTEM_TASK_UPDATED_FROM_REVIEW",
            "TASK",
            existing.id(),
            traceId,
            Map.of(
                "task_type", REVIEW_FOLLOW_UP_TASK_TYPE,
                "review_item_id", reviewState.id(),
                "completion_status", completionStatus.name()
            )
        );
        return existing.id();
    }

    private UUID createFollowUpTask(ReviewStateView reviewState,
                                    ReviewCompletionStatus completionStatus,
                                    ReviewCompletionReason completionReason,
                                    UUID traceId) {
        if (completionStatus == ReviewCompletionStatus.COMPLETED) {
            return null;
        }
        FollowUpTaskSpec spec = buildFollowUpTaskSpec(reviewState, completionStatus, completionReason);
        com.noteops.agent.application.task.TaskApplicationService.TaskView task = taskRepository.create(
            reviewState.userId(),
            reviewState.noteId(),
            TaskSource.SYSTEM,
            REVIEW_FOLLOW_UP_TASK_TYPE,
            spec.title(),
            spec.description(),
            TaskStatus.TODO,
            spec.priority(),
            spec.dueAt(),
            TaskRelatedEntityType.REVIEW,
            reviewState.id()
        );
        userActionEventRepository.append(
            reviewState.userId(),
            "SYSTEM_TASK_CREATED_FROM_REVIEW",
            "TASK",
            task.id(),
            traceId,
            Map.of(
                "task_type", REVIEW_FOLLOW_UP_TASK_TYPE,
                "review_item_id", reviewState.id(),
                "completion_status", completionStatus.name()
            )
        );
        return task.id();
    }

    private FollowUpTaskSpec buildFollowUpTaskSpec(ReviewStateView reviewState,
                                                   ReviewCompletionStatus completionStatus,
                                                   ReviewCompletionReason completionReason) {
        String noteTitle = noteRepository.findByIdAndUserId(reviewState.noteId(), reviewState.userId())
            .map(NoteQueryService.NoteDetailView::title)
            .orElse("Note " + reviewState.noteId());
        String reason = completionReason == null ? null : completionReason.name();
        String description = reason == null
            ? "Review needs follow-up on the current summary and key points."
            : "Review needs follow-up because " + reason + ". Revisit the current summary and key points.";
        return new FollowUpTaskSpec(
            "Follow up review: " + noteTitle,
            description,
            reviewState.nextReviewAt(),
            reviewState.queueType() == ReviewQueueType.RECALL ? 90 : 70
        );
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

    private RecallFeedback validateRecallFeedback(ReviewQueueType queueType, String rawSelfRecallResult, String rawNote) {
        ReviewSelfRecallResult selfRecallResult = parseOptionalSelfRecallResult(rawSelfRecallResult);
        String note = blankToNull(rawNote);
        if (queueType == ReviewQueueType.SCHEDULE) {
            if (selfRecallResult != null || note != null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RECALL_FEEDBACK_NOT_ALLOWED",
                    "self_recall_result and note are only supported for RECALL queue"
                );
            }
            return new RecallFeedback(null, null);
        }
        if (selfRecallResult == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_SELF_RECALL_RESULT", "self_recall_result is required for RECALL queue");
        }
        return new RecallFeedback(selfRecallResult, note);
    }

    private ReviewSelfRecallResult parseOptionalSelfRecallResult(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return ReviewSelfRecallResult.valueOf(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SELF_RECALL_RESULT", "self_recall_result is invalid");
        }
    }

    private String blankToNull(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim();
    }

    private ZoneOffset parseTimezoneOffset(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneOffset.of(rawValue.trim());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE_OFFSET", "timezone_offset must be a valid UTC offset");
        }
    }

    private Instant endOfDay(Instant now, ZoneOffset timezoneOffset) {
        return LocalDate.ofInstant(now, timezoneOffset)
            .plusDays(1)
            .atStartOfDay()
            .minusNanos(1)
            .toInstant(timezoneOffset);
    }

    private BigDecimal increase(BigDecimal current, int delta) {
        return current.add(BigDecimal.valueOf(delta)).min(HUNDRED);
    }

    private BigDecimal decrease(BigDecimal current, int delta) {
        BigDecimal updated = current.subtract(BigDecimal.valueOf(delta));
        return updated.max(BigDecimal.ZERO);
    }

    public record CompleteReviewCommand(
        String userId,
        String completionStatus,
        String completionReason,
        String selfRecallResult,
        String note
    ) {
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
        ReviewSelfRecallResult selfRecallResult,
        String note,
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
        ReviewSelfRecallResult selfRecallResult,
        String note,
        Instant nextReviewAt,
        int retryAfterHours,
        int unfinishedCount,
        BigDecimal masteryScore
    ) {
    }

    private record RecallFeedback(ReviewSelfRecallResult selfRecallResult, String note) {
    }

    private record FollowUpTaskSpec(String title, String description, Instant dueAt, int priority) {
    }
}
