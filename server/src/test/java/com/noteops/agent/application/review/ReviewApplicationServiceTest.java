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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-16T01:00:00Z");

    @Test
    void lazilyCreatesScheduleForNotesWithoutReviewState() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "First"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository);

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().queueType()).isEqualTo(ReviewQueueType.SCHEDULE);
        assertThat(items.getFirst().completionStatus()).isEqualTo(ReviewCompletionStatus.NOT_STARTED);
        assertThat(reviewStateRepository.states).hasSize(1);
    }

    @Test
    void sortsRecallBeforeScheduleInTodayList() {
        UUID userId = UUID.randomUUID();
        UUID noteA = UUID.randomUUID();
        UUID noteB = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteA, "Schedule note"));
        noteRepository.notes.add(noteSummary(userId, noteB, "Recall note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        reviewStateRepository.create(userId, noteA, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            BigDecimal.ZERO, null, NOW, 0, 0);
        reviewStateRepository.create(userId, noteB, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            BigDecimal.ZERO, null, NOW, 1, 24);

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository);

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

        assertThat(items).extracting(ReviewApplicationService.ReviewTodayItemView::queueType)
            .containsExactly(ReviewQueueType.RECALL, ReviewQueueType.SCHEDULE, ReviewQueueType.SCHEDULE);
    }

    @Test
    void completesScheduleAndKeepsNormalSchedulingPath() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            BigDecimal.valueOf(10), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null)
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.COMPLETED);
        assertThat(result.nextReviewAt()).isEqualTo(NOW.plusSeconds(3 * 24 * 3600));
        assertThat(result.masteryScore()).isEqualByComparingTo("30");
        assertThat(reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.RECALL)).isEmpty();
    }

    @Test
    void partialScheduleCreatesOrUpdatesRecall() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            BigDecimal.valueOf(50), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT")
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.PARTIAL);
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.RECALL)
            .orElseThrow();
        assertThat(recall.retryAfterHours()).isEqualTo(24);
        assertThat(recall.unfinishedCount()).isEqualTo(1);
        assertThat(recall.completionReason()).isEqualTo(ReviewCompletionReason.TIME_LIMIT);
    }

    @Test
    void completedRecallRestoresScheduleWindow() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        reviewStateRepository.create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            BigDecimal.valueOf(20), NOW.minusSeconds(3600), NOW.plusSeconds(3600), 0, 0);
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            BigDecimal.valueOf(20), NOW.minusSeconds(3600), NOW, 1, 24
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            recall.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null)
        );

        assertThat(result.nextReviewAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.SCHEDULE)
            .orElseThrow();
        assertThat(schedule.nextReviewAt()).isEqualTo(NOW.plusSeconds(3 * 24 * 3600));
        assertThat(schedule.completionStatus()).isEqualTo(ReviewCompletionStatus.COMPLETED);
    }

    @Test
    void rejectsMissingReasonForNonCompletedStatus() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            BigDecimal.ZERO, null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", null)
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("completion_reason is required");
    }

    private ReviewApplicationService newService(InMemoryReviewStateRepository reviewStateRepository,
                                               InMemoryNoteRepository noteRepository) {
        return new ReviewApplicationService(
            reviewStateRepository,
            noteRepository,
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private NoteQueryService.NoteSummaryView noteSummary(UUID userId, UUID noteId, String title) {
        return new NoteQueryService.NoteSummaryView(
            noteId,
            userId,
            title,
            "summary",
            List.of("point"),
            UUID.randomUUID(),
            NOW
        );
    }

    private static final class InMemoryReviewStateRepository implements ReviewStateRepository {

        private final Map<UUID, ReviewApplicationService.ReviewStateView> states = new HashMap<>();

        @Override
        public void createInitialScheduleIfMissing(UUID userId, UUID noteId, Instant now) {
            boolean exists = states.values().stream()
                .anyMatch(view -> view.userId().equals(userId)
                    && view.noteId().equals(noteId)
                    && view.queueType() == ReviewQueueType.SCHEDULE);
            if (!exists) {
                create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
                    BigDecimal.ZERO, null, now, 0, 0);
            }
        }

        @Override
        public List<ReviewApplicationService.ReviewStateView> findDueByUserId(UUID userId, Instant now) {
            return states.values().stream()
                .filter(view -> view.userId().equals(userId))
                .filter(view -> !view.nextReviewAt().isAfter(now))
                .sorted((left, right) -> {
                    int queueCompare = left.queueType() == right.queueType() ? 0 : (left.queueType() == ReviewQueueType.RECALL ? -1 : 1);
                    if (queueCompare != 0) {
                        return queueCompare;
                    }
                    int nextReviewCompare = left.nextReviewAt().compareTo(right.nextReviewAt());
                    if (nextReviewCompare != 0) {
                        return nextReviewCompare;
                    }
                    return left.createdAt().compareTo(right.createdAt());
                })
                .toList();
        }

        @Override
        public Optional<ReviewApplicationService.ReviewStateView> findByIdAndUserId(UUID reviewStateId, UUID userId) {
            return Optional.ofNullable(states.get(reviewStateId))
                .filter(view -> view.userId().equals(userId));
        }

        @Override
        public Optional<ReviewApplicationService.ReviewStateView> findByUserIdAndNoteIdAndQueueType(UUID userId, UUID noteId, ReviewQueueType queueType) {
            return states.values().stream()
                .filter(view -> view.userId().equals(userId))
                .filter(view -> view.noteId().equals(noteId))
                .filter(view -> view.queueType() == queueType)
                .findFirst();
        }

        @Override
        public ReviewApplicationService.ReviewStateView create(UUID userId,
                                                               UUID noteId,
                                                               ReviewQueueType queueType,
                                                               ReviewCompletionStatus completionStatus,
                                                               ReviewCompletionReason completionReason,
                                                               BigDecimal masteryScore,
                                                               Instant lastReviewedAt,
                                                               Instant nextReviewAt,
                                                               int unfinishedCount,
                                                               int retryAfterHours) {
            UUID id = UUID.randomUUID();
            Instant createdAt = NOW.minusSeconds(states.size());
            ReviewApplicationService.ReviewStateView view = new ReviewApplicationService.ReviewStateView(
                id,
                userId,
                noteId,
                queueType,
                masteryScore,
                lastReviewedAt,
                nextReviewAt,
                completionStatus,
                completionReason,
                unfinishedCount,
                retryAfterHours,
                createdAt,
                createdAt
            );
            states.put(id, view);
            return view;
        }

        @Override
        public void update(UUID reviewStateId,
                           ReviewCompletionStatus completionStatus,
                           ReviewCompletionReason completionReason,
                           BigDecimal masteryScore,
                           Instant lastReviewedAt,
                           Instant nextReviewAt,
                           int unfinishedCount,
                           int retryAfterHours) {
            ReviewApplicationService.ReviewStateView current = states.get(reviewStateId);
            states.put(reviewStateId, new ReviewApplicationService.ReviewStateView(
                current.id(),
                current.userId(),
                current.noteId(),
                current.queueType(),
                masteryScore,
                lastReviewedAt,
                nextReviewAt,
                completionStatus,
                completionReason,
                unfinishedCount,
                retryAfterHours,
                current.createdAt(),
                NOW
            ));
        }
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final List<NoteQueryService.NoteSummaryView> notes = new ArrayList<>();

        @Override
        public NoteCreationResult create(UUID userId, String title, String currentSummary, List<String> currentKeyPoints, String sourceUri, String rawText, String cleanText, Map<String, Object> sourceSnapshot, Map<String, Object> analysisResult) {
            return new NoteCreationResult(UUID.randomUUID(), UUID.randomUUID());
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return notes.stream().filter(note -> note.userId().equals(userId)).toList();
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            return UUID.randomUUID();
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
        }
    }
}
