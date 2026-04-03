package com.noteops.agent.service.review;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.review.ReviewSelfRecallResult;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.review.ReviewStateRepository;
import com.noteops.agent.repository.task.TaskRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
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
            null, null, BigDecimal.ZERO, null, NOW, 0, 0);
        reviewStateRepository.create(userId, noteB, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            null, null, BigDecimal.ZERO, null, NOW, 1, 24);

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository);

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

        assertThat(items).extracting(ReviewApplicationService.ReviewTodayItemView::queueType)
            .containsExactly(ReviewQueueType.RECALL, ReviewQueueType.SCHEDULE, ReviewQueueType.SCHEDULE);
    }

    @Test
    void returnsUpcomingReviewsAfterTodayBoundary() {
        UUID userId = UUID.randomUUID();
        UUID todayNoteId = UUID.randomUUID();
        UUID tomorrowNoteId = UUID.randomUUID();
        UUID tomorrowRecallNoteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, todayNoteId, "Today"));
        noteRepository.notes.add(noteSummary(userId, tomorrowNoteId, "Tomorrow schedule"));
        noteRepository.notes.add(noteSummary(userId, tomorrowRecallNoteId, "Tomorrow recall"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        reviewStateRepository.create(userId, todayNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, Instant.parse("2026-03-16T08:00:00Z"), 0, 0);
        reviewStateRepository.create(userId, tomorrowNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, Instant.parse("2026-03-17T02:00:00Z"), 0, 0);
        reviewStateRepository.create(userId, tomorrowRecallNoteId, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            null, null, BigDecimal.ZERO, null, Instant.parse("2026-03-17T02:00:00Z"), 1, 24);

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository);

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listUpcoming(userId.toString(), "+08:00");

        assertThat(items).extracting(ReviewApplicationService.ReviewTodayItemView::title)
            .containsExactly("Tomorrow recall", "Tomorrow schedule");
    }

    @Test
    void listTodayReturnsBaseFieldsWithoutAiEnrichment() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "AI note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        reviewStateRepository.create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, NOW, 0, 0);
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            new InMemoryTaskRepository(),
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new RecordingReviewAiAssistant()
        );

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().title()).isEqualTo("AI note");
        assertThat(items.getFirst().currentSummary()).isEqualTo("summary");
        assertThat(items.getFirst().aiRecallSummary()).isNull();
        assertThat(items.getFirst().aiReviewKeyPoints()).isEmpty();
        assertThat(items.getFirst().aiExtensionPreview()).isNull();
        assertThat(traceRepository.created).isNull();
        assertThat(traceRepository.completed).isNull();
        assertThat(toolInvocationLogRepository.logs).isEmpty();
        assertThat(eventRepository.events).isEmpty();
    }

    @Test
    void listTodayDoesNotInvokeAiAssistantEvenWhenOneIsConfigured() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Fallback note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        reviewStateRepository.create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, NOW, 0, 0);
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            new InMemoryTaskRepository(),
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new ThrowingReviewAiAssistant("simulated render failure")
        );

        List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().aiRecallSummary()).isNull();
        assertThat(items.getFirst().aiReviewKeyPoints()).isEmpty();
        assertThat(items.getFirst().aiExtensionPreview()).isNull();
        assertThat(items.getFirst().currentSummary()).isEqualTo("summary");
        assertThat(traceRepository.created).isNull();
        assertThat(traceRepository.failed).isNull();
        assertThat(toolInvocationLogRepository.logs).isEmpty();
        assertThat(eventRepository.events).isEmpty();
    }

    @Test
    void getPrepReturnsAiRenderAndWritesGovernanceArtifacts() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Prep note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView review = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(10), null, NOW, 0, 0
        );
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new RecordingReviewAiAssistant()
        );

        ReviewApplicationService.ReviewPrepView result = service.getPrep(review.id().toString(), userId.toString());

        assertThat(result.reviewItemId()).isEqualTo(review.id());
        assertThat(result.aiRecallSummary()).isEqualTo("AI recall summary");
        assertThat(result.aiReviewKeyPoints()).containsExactly("AI point 1", "AI point 2");
        assertThat(result.aiExtensionPreview()).isEqualTo("AI extension preview");
        assertThat(reviewStateRepository.findByIdAndUserId(review.id(), userId)).get()
            .extracting(ReviewApplicationService.ReviewStateView::completionStatus)
            .isEqualTo(ReviewCompletionStatus.NOT_STARTED);
        assertThat(traceRepository.created.entryType()).isEqualTo("REVIEW_AI_PREP");
        assertThat(traceRepository.completed.traceId()).isEqualTo(traceRepository.traceId);
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.ai-prep");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("REVIEW_PREP_RENDERED");
        assertThat(taskRepository.lastTaskId).isNull();
    }

    @Test
    void getPrepFallsBackWhenAssistantThrows() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Prep fallback note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView review = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(10), null, NOW, 0, 0
        );
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new ThrowingReviewAiAssistant("simulated render failure")
        );

        ReviewApplicationService.ReviewPrepView result = service.getPrep(review.id().toString(), userId.toString());

        assertThat(result.aiRecallSummary()).isNull();
        assertThat(result.aiReviewKeyPoints()).isEmpty();
        assertThat(result.aiExtensionPreview()).isNull();
        assertThat(traceRepository.failed).isNotNull();
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.ai-prep");
        assertThat(toolInvocationLogRepository.logs.getFirst().status()).isEqualTo("FAILED");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("REVIEW_PREP_DEGRADED");
        assertThat(taskRepository.lastTaskId).isNull();
    }

    @Test
    void getFeedbackReturnsAiFeedbackAndWritesGovernanceArtifacts() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Feedback note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView review = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            ReviewSelfRecallResult.VAGUE, "stuck on one point", BigDecimal.valueOf(30), NOW.minusSeconds(300), NOW, 1, 24
        );
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new CompletionFeedbackReviewAiAssistant()
        );

        ReviewApplicationService.ReviewFeedbackView result = service.getFeedback(review.id().toString(), userId.toString());

        assertThat(result.reviewItemId()).isEqualTo(review.id());
        assertThat(result.recallFeedbackSummary()).isEqualTo("AI feedback summary");
        assertThat(result.nextReviewHint()).isEqualTo("AI next hint");
        assertThat(result.extensionSuggestions()).containsExactly("AI suggestion 1", "AI suggestion 2");
        assertThat(result.followUpTaskSuggestion()).isEqualTo("AI follow-up task suggestion");
        assertThat(reviewStateRepository.findByIdAndUserId(review.id(), userId)).get()
            .extracting(ReviewApplicationService.ReviewStateView::completionStatus)
            .isEqualTo(ReviewCompletionStatus.PARTIAL);
        assertThat(traceRepository.created.entryType()).isEqualTo("REVIEW_AI_FEEDBACK");
        assertThat(traceRepository.completed.traceId()).isEqualTo(traceRepository.traceId);
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("ai_feedback_status", "COMPLETED");
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.ai-feedback");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly(
                "REVIEW_FEEDBACK_GENERATED",
                "REVIEW_EXTENSION_SUGGESTIONS_GENERATED",
                "REVIEW_FOLLOW_UP_TASK_SUGGESTED"
            );
        assertThat(taskRepository.lastTaskId).isNull();
    }

    @Test
    void getFeedbackFallsBackToExistingFallbackWhenAssistantThrows() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Feedback fallback note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView review = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            ReviewSelfRecallResult.VAGUE, "stuck on one point", BigDecimal.valueOf(30), NOW.minusSeconds(300), NOW, 1, 24
        );
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new ThrowingReviewAiAssistant("simulated feedback failure")
        );

        ReviewApplicationService.ReviewFeedbackView result = service.getFeedback(review.id().toString(), userId.toString());

        assertThat(result.recallFeedbackSummary()).isEqualTo("本次复习只覆盖了部分内容，重点缺口需要回到关键点继续补齐。");
        assertThat(result.nextReviewHint()).isEqualTo("下一次先只盯住最难回忆的 1 到 2 个关键点，不要同时补全部内容。");
        assertThat(result.extensionSuggestions()).containsExactly(
            "回看当前关键点里最模糊的部分",
            "补一条简短自我说明，写下卡住的位置"
        );
        assertThat(result.followUpTaskSuggestion()).isEqualTo("可考虑创建一条 follow-up task：回看 Feedback fallback note 中最模糊的关键点。");
        assertThat(traceRepository.failed).isNotNull();
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.ai-feedback");
        assertThat(toolInvocationLogRepository.logs.getFirst().status()).isEqualTo("FAILED");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("REVIEW_FEEDBACK_DEGRADED");
        assertThat(taskRepository.lastTaskId).isNull();
    }

    @Test
    void completesScheduleAndKeepsNormalSchedulingPath() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(10), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(
            reviewStateRepository,
            new InMemoryNoteRepository(),
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository
        );

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null, null, null)
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.COMPLETED);
        assertThat(result.nextReviewAt()).isEqualTo(NOW.plusSeconds(3 * 24 * 3600));
        assertThat(result.masteryScore()).isEqualByComparingTo("30");
        assertThat(result.recallFeedbackSummary()).isNull();
        assertThat(result.nextReviewHint()).isNull();
        assertThat(result.extensionSuggestions()).isEmpty();
        assertThat(result.followUpTaskSuggestion()).isNull();
        assertThat(reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.RECALL)).isEmpty();
        assertThat(traceRepository.created.entryType()).isEqualTo("REVIEW_COMPLETE");
        assertThat(traceRepository.completed.traceId()).isEqualTo(traceRepository.traceId);
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("REVIEW_COMPLETED");
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.complete");
        assertThat(toolInvocationLogRepository.logs.getFirst().status()).isEqualTo("COMPLETED");
        assertThat(toolInvocationLogRepository.logs.getFirst().outputDigest()).doesNotContainKey("follow_up_task_id");
    }

    @Test
    void partialScheduleCreatesOrUpdatesRecall() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Recall note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(50), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository, taskRepository);

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.PARTIAL);
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.RECALL)
            .orElseThrow();
        assertThat(recall.retryAfterHours()).isEqualTo(24);
        assertThat(recall.unfinishedCount()).isEqualTo(1);
        assertThat(recall.completionReason()).isEqualTo(ReviewCompletionReason.TIME_LIMIT);
        assertThat(taskRepository.findOpenByUserIdAndSourceAndTaskTypeAndNoteId(userId, TaskSource.SYSTEM, "REVIEW_FOLLOW_UP", noteId))
            .get()
            .extracting(ReviewApplicationServiceTest::taskStatus)
            .isEqualTo(TaskStatus.TODO);
    }

    @Test
    void completedRecallRestoresScheduleWindow() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Recall note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        reviewStateRepository.create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            null, null, BigDecimal.valueOf(20), NOW.minusSeconds(3600), NOW.plusSeconds(3600), 0, 0);
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.PARTIAL, ReviewCompletionReason.TIME_LIMIT,
            null, null, BigDecimal.valueOf(20), NOW.minusSeconds(3600), NOW, 1, 24
        );
        taskRepository.create(userId, noteId, TaskSource.SYSTEM, "REVIEW_FOLLOW_UP", "Existing follow-up", null,
            TaskStatus.TODO, 90, NOW.plusSeconds(24 * 3600L), TaskRelatedEntityType.REVIEW, recall.id());

        ReviewApplicationService service = newService(reviewStateRepository, noteRepository, taskRepository);

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            recall.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null, "GOOD", "Recovered after a second pass")
        );

        assertThat(result.nextReviewAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
        assertThat(result.selfRecallResult()).isEqualTo(ReviewSelfRecallResult.GOOD);
        assertThat(result.note()).isEqualTo("Recovered after a second pass");
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.findByUserIdAndNoteIdAndQueueType(userId, noteId, ReviewQueueType.SCHEDULE)
            .orElseThrow();
        assertThat(schedule.nextReviewAt()).isEqualTo(NOW.plusSeconds(3 * 24 * 3600));
        assertThat(schedule.completionStatus()).isEqualTo(ReviewCompletionStatus.COMPLETED);
        assertThat(schedule.selfRecallResult()).isNull();
        assertThat(taskRepository.findByIdAndUserId(taskRepository.lastTaskId, userId)).get()
            .extracting(ReviewApplicationServiceTest::taskStatus)
            .isEqualTo(TaskStatus.DONE);
    }

    @Test
    void partialScheduleWritesGovernanceArtifactsAndFollowUpTask() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(50), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(
            reviewStateRepository,
            new InMemoryNoteRepository(),
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository
        );

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.PARTIAL);
        assertThat(result.recallFeedbackSummary()).isNull();
        assertThat(result.nextReviewHint()).isNull();
        assertThat(result.extensionSuggestions()).isEmpty();
        assertThat(result.followUpTaskSuggestion()).isNull();
        assertThat(traceRepository.created.entryType()).isEqualTo("REVIEW_COMPLETE");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("completion_status", "PARTIAL");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("completion_reason", "TIME_LIMIT");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("SYSTEM_TASK_CREATED_FROM_REVIEW", "REVIEW_PARTIAL");
        assertThat(eventRepository.events.getLast().payload()).containsEntry("completion_status", "PARTIAL");
        assertThat(eventRepository.events.getLast().payload()).containsEntry("completion_reason", "TIME_LIMIT");
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.complete");
        ReviewToolInvocationLogRecord completeLog = toolInvocationLogRepository.logs.stream()
            .filter(log -> log.toolName().equals("review.complete"))
            .findFirst()
            .orElseThrow();
        assertThat(completeLog.status()).isEqualTo("COMPLETED");
        assertThat(completeLog.outputDigest()).containsKey("follow_up_task_id");
        assertThat(completeLog.outputDigest().get("follow_up_task_id")).isEqualTo(taskRepository.lastTaskId);
    }

    @Test
    void completeReturnsEmptyFeedbackWithoutAiInvocation() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Review note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(40), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new CompletionFeedbackReviewAiAssistant()
        );

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
        );

        assertThat(result.recallFeedbackSummary()).isNull();
        assertThat(result.nextReviewHint()).isNull();
        assertThat(result.extensionSuggestions()).isEmpty();
        assertThat(result.followUpTaskSuggestion()).isNull();
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.complete");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("SYSTEM_TASK_CREATED_FROM_REVIEW", "REVIEW_PARTIAL");
    }

    @Test
    void completeDoesNotInvokeAiFeedbackEvenIfAssistantWouldThrow() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.notes.add(noteSummary(userId, noteId, "Failing feedback note"));
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(50), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            toolInvocationLogRepository,
            new ThrowingReviewAiAssistant("simulated feedback failure")
        );

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
        );

        assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.PARTIAL);
        assertThat(result.recallFeedbackSummary()).isNull();
        assertThat(result.extensionSuggestions()).isEmpty();
        assertThat(result.followUpTaskSuggestion()).isNull();
        assertThat(toolInvocationLogRepository.logs).extracting(ReviewToolInvocationLogRecord::toolName)
            .containsExactly("review.complete");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("ai_feedback_status", "NOT_LOADED");
        assertThat(eventRepository.events).extracting(ReviewUserActionEventRecord::eventType)
            .containsExactly("SYSTEM_TASK_CREATED_FROM_REVIEW", "REVIEW_PARTIAL");
    }

    @Test
    void completeLeavesAiFeedbackEmptyForAllStatuses() {
        UUID userId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        UUID completedNoteId = UUID.randomUUID();
        UUID partialNoteId = UUID.randomUUID();
        UUID notStartedNoteId = UUID.randomUUID();
        UUID abandonedNoteId = UUID.randomUUID();
        noteRepository.notes.add(noteSummary(userId, completedNoteId, "Completed note"));
        noteRepository.notes.add(noteSummary(userId, partialNoteId, "Partial note"));
        noteRepository.notes.add(noteSummary(userId, notStartedNoteId, "Not started note"));
        noteRepository.notes.add(noteSummary(userId, abandonedNoteId, "Abandoned note"));
        ReviewApplicationService.ReviewStateView completed = reviewStateRepository.create(
            userId, completedNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(30), null, NOW, 0, 0
        );
        ReviewApplicationService.ReviewStateView partial = reviewStateRepository.create(
            userId, partialNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(30), null, NOW, 0, 0
        );
        ReviewApplicationService.ReviewStateView notStarted = reviewStateRepository.create(
            userId, notStartedNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(30), null, NOW, 0, 0
        );
        ReviewApplicationService.ReviewStateView abandoned = reviewStateRepository.create(
            userId, abandonedNoteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(30), null, NOW, 0, 0
        );
        ReviewApplicationService service = newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            new RecordingAgentTraceRepository(),
            new RecordingUserActionEventRepository(),
            new RecordingToolInvocationLogRepository(),
            new ThrowingReviewAiAssistant("ai should not be invoked")
        );

        ReviewApplicationService.ReviewCompletionView completedView = service.complete(
            completed.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null, null, null)
        );
        ReviewApplicationService.ReviewCompletionView partialView = service.complete(
            partial.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
        );
        ReviewApplicationService.ReviewCompletionView notStartedView = service.complete(
            notStarted.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "NOT_STARTED", "DEFERRED", null, null)
        );
        ReviewApplicationService.ReviewCompletionView abandonedView = service.complete(
            abandoned.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "ABANDONED", "TOO_HARD", null, null)
        );

        assertThat(completedView.recallFeedbackSummary()).isNull();
        assertThat(completedView.nextReviewHint()).isNull();
        assertThat(completedView.extensionSuggestions()).isEmpty();
        assertThat(completedView.followUpTaskSuggestion()).isNull();
        assertThat(partialView.recallFeedbackSummary()).isNull();
        assertThat(partialView.nextReviewHint()).isNull();
        assertThat(partialView.extensionSuggestions()).isEmpty();
        assertThat(partialView.followUpTaskSuggestion()).isNull();
        assertThat(notStartedView.recallFeedbackSummary()).isNull();
        assertThat(notStartedView.nextReviewHint()).isNull();
        assertThat(notStartedView.extensionSuggestions()).isEmpty();
        assertThat(notStartedView.followUpTaskSuggestion()).isNull();
        assertThat(abandonedView.recallFeedbackSummary()).isNull();
        assertThat(abandonedView.nextReviewHint()).isNull();
        assertThat(abandonedView.extensionSuggestions()).isEmpty();
        assertThat(abandonedView.followUpTaskSuggestion()).isNull();
    }

    @Test
    void rejectsMissingReasonForNonCompletedStatus() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", null, null, null)
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("completion_reason is required");
    }

    @Test
    void recallRequiresSelfRecallResult() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.complete(
            recall.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null, null, "Need more time")
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("self_recall_result is required for RECALL queue");
    }

    @Test
    void scheduleRejectsRecallFeedbackFields() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.ZERO, null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.complete(
            schedule.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "COMPLETED", null, "GOOD", "Should be rejected")
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("self_recall_result and note are only supported for RECALL queue");
    }

    @Test
    void blankRecallNoteIsNormalizedToNull() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
        ReviewApplicationService.ReviewStateView recall = reviewStateRepository.create(
            userId, noteId, ReviewQueueType.RECALL, ReviewCompletionStatus.NOT_STARTED, null,
            null, null, BigDecimal.valueOf(25), null, NOW, 0, 0
        );

        ReviewApplicationService service = newService(reviewStateRepository, new InMemoryNoteRepository());

        ReviewApplicationService.ReviewCompletionView result = service.complete(
            recall.id().toString(),
            new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", "VAGUE", "   ")
        );

        assertThat(result.selfRecallResult()).isEqualTo(ReviewSelfRecallResult.VAGUE);
        assertThat(result.note()).isNull();
        assertThat(reviewStateRepository.findByIdAndUserId(recall.id(), userId)).get()
            .extracting(ReviewApplicationService.ReviewStateView::note)
            .isNull();
    }

    private ReviewApplicationService newService(InMemoryReviewStateRepository reviewStateRepository,
                                               InMemoryNoteRepository noteRepository) {
        return newService(reviewStateRepository, noteRepository, new InMemoryTaskRepository());
    }

    private ReviewApplicationService newService(InMemoryReviewStateRepository reviewStateRepository,
                                               InMemoryNoteRepository noteRepository,
                                               InMemoryTaskRepository taskRepository) {
        return new ReviewApplicationService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            (userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage) -> {
            },
            new NoOpReviewAiAssistant(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ReviewApplicationService newService(InMemoryReviewStateRepository reviewStateRepository,
                                               InMemoryNoteRepository noteRepository,
                                               InMemoryTaskRepository taskRepository,
                                               AgentTraceRepository agentTraceRepository,
                                               UserActionEventRepository userActionEventRepository,
                                               ToolInvocationLogRepository toolInvocationLogRepository) {
        return newService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            toolInvocationLogRepository,
            new NoOpReviewAiAssistant()
        );
    }

    private ReviewApplicationService newService(InMemoryReviewStateRepository reviewStateRepository,
                                               InMemoryNoteRepository noteRepository,
                                               InMemoryTaskRepository taskRepository,
                                               AgentTraceRepository agentTraceRepository,
                                               UserActionEventRepository userActionEventRepository,
                                               ToolInvocationLogRepository toolInvocationLogRepository,
                                               ReviewAiAssistant reviewAiAssistant) {
        return new ReviewApplicationService(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            toolInvocationLogRepository,
            reviewAiAssistant,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static TaskStatus taskStatus(com.noteops.agent.service.task.TaskApplicationService.TaskView task) {
        return task.status();
    }

    private NoteQueryService.NoteSummaryView noteSummary(UUID userId, UUID noteId, String title) {
        return new NoteQueryService.NoteSummaryView(
            noteId,
            userId,
            title,
            "summary",
            List.of("point"),
            List.of("tag"),
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
                    null, null, BigDecimal.ZERO, null, now, 0, 0);
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
        public List<ReviewApplicationService.ReviewStateView> findUpcomingByUserId(UUID userId, Instant nextReviewAfterExclusive) {
            return states.values().stream()
                .filter(view -> view.userId().equals(userId))
                .filter(view -> view.nextReviewAt() != null && view.nextReviewAt().isAfter(nextReviewAfterExclusive))
                .sorted((left, right) -> {
                    int nextReviewCompare = left.nextReviewAt().compareTo(right.nextReviewAt());
                    if (nextReviewCompare != 0) {
                        return nextReviewCompare;
                    }
                    int queueCompare = left.queueType() == right.queueType() ? 0 : (left.queueType() == ReviewQueueType.RECALL ? -1 : 1);
                    if (queueCompare != 0) {
                        return queueCompare;
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
                                                               ReviewSelfRecallResult selfRecallResult,
                                                               String note,
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
                selfRecallResult,
                note,
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
                           ReviewSelfRecallResult selfRecallResult,
                           String note,
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
                selfRecallResult,
                note,
                unfinishedCount,
                retryAfterHours,
                current.createdAt(),
                NOW
            ));
        }
    }

    private static final class InMemoryTaskRepository implements TaskRepository {

        private final Map<UUID, com.noteops.agent.service.task.TaskApplicationService.TaskView> tasks = new HashMap<>();
        private UUID lastTaskId;

        @Override
        public com.noteops.agent.service.task.TaskApplicationService.TaskView create(UUID userId,
                                                                                         UUID noteId,
                                                                                         TaskSource taskSource,
                                                                                         String taskType,
                                                                                         String title,
                                                                                         String description,
                                                                                         TaskStatus status,
                                                                                         int priority,
                                                                                         Instant dueAt,
                                                                                         TaskRelatedEntityType relatedEntityType,
                                                                                         UUID relatedEntityId) {
            UUID taskId = UUID.randomUUID();
            lastTaskId = taskId;
            com.noteops.agent.service.task.TaskApplicationService.TaskView view =
                new com.noteops.agent.service.task.TaskApplicationService.TaskView(
                    taskId,
                    userId,
                    noteId,
                    taskSource,
                    taskType,
                    title,
                    description,
                    status,
                    priority,
                    dueAt,
                    relatedEntityType,
                    relatedEntityId,
                    NOW,
                    NOW
                );
            tasks.put(taskId, view);
            return view;
        }

        @Override
        public List<com.noteops.agent.service.task.TaskApplicationService.TaskView> findTodayByUserId(UUID userId, Instant dueAtInclusive) {
            return List.of();
        }

        @Override
        public List<com.noteops.agent.service.task.TaskApplicationService.TaskView> findUpcomingByUserId(UUID userId, Instant dueAfterExclusive) {
            return List.of();
        }

        @Override
        public Optional<com.noteops.agent.service.task.TaskApplicationService.TaskView> findByIdAndUserId(UUID taskId, UUID userId) {
            return Optional.ofNullable(tasks.get(taskId))
                .filter(task -> task.userId().equals(userId));
        }

        @Override
        public Optional<com.noteops.agent.service.task.TaskApplicationService.TaskView> findOpenByUserIdAndSourceAndTaskTypeAndNoteId(UUID userId,
                                                                                                                                        TaskSource taskSource,
                                                                                                                                        String taskType,
                                                                                                                                        UUID noteId) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.taskSource() == taskSource)
                .filter(task -> task.taskType().equals(taskType))
                .filter(task -> noteId.equals(task.noteId()))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .findFirst();
        }

        @Override
        public Optional<com.noteops.agent.service.task.TaskApplicationService.TaskView> findOpenDuplicateUserTask(UUID userId,
                                                                                                                       String title,
                                                                                                                       String taskType,
                                                                                                                       UUID noteId,
                                                                                                                       TaskRelatedEntityType relatedEntityType,
                                                                                                                       UUID relatedEntityId) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.taskSource() == TaskSource.USER)
                .filter(task -> task.title().equals(title))
                .filter(task -> task.taskType().equals(taskType))
                .filter(task -> task.relatedEntityType() == relatedEntityType)
                .filter(task -> java.util.Objects.equals(task.noteId(), noteId))
                .filter(task -> java.util.Objects.equals(task.relatedEntityId(), relatedEntityId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .findFirst();
        }

        @Override
        public void updateStatus(UUID taskId, TaskStatus status) {
            com.noteops.agent.service.task.TaskApplicationService.TaskView existing = tasks.get(taskId);
            tasks.put(taskId, new com.noteops.agent.service.task.TaskApplicationService.TaskView(
                existing.id(),
                existing.userId(),
                existing.noteId(),
                existing.taskSource(),
                existing.taskType(),
                existing.title(),
                existing.description(),
                status,
                existing.priority(),
                existing.dueAt(),
                existing.relatedEntityType(),
                existing.relatedEntityId(),
                existing.createdAt(),
                NOW
            ));
        }

        @Override
        public void refreshOpenTask(UUID taskId,
                                    String title,
                                    String description,
                                    int priority,
                                    Instant dueAt,
                                    TaskRelatedEntityType relatedEntityType,
                                    UUID relatedEntityId) {
            com.noteops.agent.service.task.TaskApplicationService.TaskView existing = tasks.get(taskId);
            tasks.put(taskId, new com.noteops.agent.service.task.TaskApplicationService.TaskView(
                existing.id(),
                existing.userId(),
                existing.noteId(),
                existing.taskSource(),
                existing.taskType(),
                title,
                description,
                existing.status(),
                priority,
                dueAt,
                relatedEntityType,
                relatedEntityId,
                existing.createdAt(),
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

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final UUID traceId = UUID.randomUUID();
        private TraceCreateRecord created;
        private TraceCompletionRecord completed;
        private TraceCompletionRecord failed;

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            created = new TraceCreateRecord(userId, entryType, goal, rootEntityType, rootEntityId, workerSequence, orchestratorState);
            return traceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completed = new TraceCompletionRecord(traceId, resultSummary, orchestratorState);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            failed = new TraceCompletionRecord(traceId, resultSummary, orchestratorState);
        }
    }

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {

        private final List<ReviewUserActionEventRecord> events = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            events.add(new ReviewUserActionEventRecord(userId, eventType, entityType, entityId, traceId, payload));
        }
    }

    private static final class RecordingToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final List<ReviewToolInvocationLogRecord> logs = new ArrayList<>();

        @Override
        public void append(UUID userId,
                           UUID traceId,
                           String toolName,
                           String status,
                           Map<String, Object> inputDigest,
                           Map<String, Object> outputDigest,
                           Integer latencyMs,
                           String errorCode,
                           String errorMessage) {
            logs.add(new ReviewToolInvocationLogRecord(userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage));
        }
    }

    private record TraceCreateRecord(
        UUID userId,
        String entryType,
        String goal,
        String rootEntityType,
        UUID rootEntityId,
        List<String> workerSequence,
        Map<String, Object> orchestratorState
    ) {
    }

    private record TraceCompletionRecord(
        UUID traceId,
        String resultSummary,
        Map<String, Object> orchestratorState
    ) {
    }

    private record ReviewUserActionEventRecord(
        UUID userId,
        String eventType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload
    ) {
    }

    private record ReviewToolInvocationLogRecord(
        UUID userId,
        UUID traceId,
        String toolName,
        String status,
        Map<String, Object> inputDigest,
        Map<String, Object> outputDigest,
        Integer latencyMs,
        String errorCode,
        String errorMessage
    ) {
    }

    private static final class NoOpReviewAiAssistant implements ReviewAiAssistant {

        @Override
        public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
            return new ReviewRenderResult(Map.of());
        }

        @Override
        public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
            return new ReviewFeedbackResult(null, null, List.of(), null);
        }
    }

    private static final class RecordingReviewAiAssistant implements ReviewAiAssistant {

        @Override
        public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
            UUID reviewItemId = request.items().getFirst().reviewItemId();
            return new ReviewRenderResult(Map.of(
                reviewItemId,
                new RenderedReviewItem(reviewItemId, "AI recall summary", List.of("AI point 1", "AI point 2"), "AI extension preview")
            ));
        }

        @Override
        public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
            return new ReviewFeedbackResult("unused", "unused", List.of(), null);
        }
    }

    private static final class CompletionFeedbackReviewAiAssistant implements ReviewAiAssistant {

        @Override
        public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
            return new ReviewRenderResult(Map.of());
        }

        @Override
        public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
            return new ReviewFeedbackResult(
                "AI feedback summary",
                "AI next hint",
                List.of("AI suggestion 1", "AI suggestion 2"),
                "AI follow-up task suggestion"
            );
        }
    }

    private static final class ThrowingReviewAiAssistant implements ReviewAiAssistant {

        private final String message;

        private ThrowingReviewAiAssistant(String message) {
            this.message = message;
        }

        @Override
        public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
            throw new RuntimeException(message);
        }

        @Override
        public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
            throw new RuntimeException(message);
        }
    }
}
