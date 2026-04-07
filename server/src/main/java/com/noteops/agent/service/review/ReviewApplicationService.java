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
import java.util.ArrayList;
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
    private static final ToolInvocationLogRepository NO_OP_TOOL_INVOCATION_LOG_REPOSITORY = (userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage) -> {
    };
    private static final ReviewAiAssistant NO_OP_REVIEW_AI_ASSISTANT = new ReviewAiAssistant() {
        @Override
        public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
            return new ReviewRenderResult(Map.of());
        }

        @Override
        public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
            return new ReviewFeedbackResult(null, null, List.of(), null);
        }
    };

    private final ReviewStateRepository reviewStateRepository;
    private final NoteRepository noteRepository;
    private final TaskRepository taskRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final ReviewAiAssistant reviewAiAssistant;
    private final Clock clock;

    @Autowired
    public ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                                    NoteRepository noteRepository,
                                    TaskRepository taskRepository,
                                    AgentTraceRepository agentTraceRepository,
                                    UserActionEventRepository userActionEventRepository,
                                    ToolInvocationLogRepository toolInvocationLogRepository,
                                    ReviewAiAssistant reviewAiAssistant) {
        this(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            toolInvocationLogRepository,
            reviewAiAssistant,
            Clock.systemUTC()
        );
    }

    public ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                                    NoteRepository noteRepository,
                                    TaskRepository taskRepository,
                                    AgentTraceRepository agentTraceRepository,
                                    UserActionEventRepository userActionEventRepository) {
        this(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            NO_OP_TOOL_INVOCATION_LOG_REPOSITORY,
            NO_OP_REVIEW_AI_ASSISTANT,
            Clock.systemUTC()
        );
    }

    ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                             NoteRepository noteRepository,
                             TaskRepository taskRepository,
                             AgentTraceRepository agentTraceRepository,
                             UserActionEventRepository userActionEventRepository,
                             Clock clock) {
        this(
            reviewStateRepository,
            noteRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            NO_OP_TOOL_INVOCATION_LOG_REPOSITORY,
            NO_OP_REVIEW_AI_ASSISTANT,
            clock
        );
    }

    ReviewApplicationService(ReviewStateRepository reviewStateRepository,
                             NoteRepository noteRepository,
                             TaskRepository taskRepository,
                             AgentTraceRepository agentTraceRepository,
                             UserActionEventRepository userActionEventRepository,
                             ToolInvocationLogRepository toolInvocationLogRepository,
                             ReviewAiAssistant reviewAiAssistant,
                             Clock clock) {
        this.reviewStateRepository = reviewStateRepository;
        this.noteRepository = noteRepository;
        this.taskRepository = taskRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.reviewAiAssistant = reviewAiAssistant;
        this.clock = clock;
    }

    // 构建今日 review 队列，并补齐缺失的初始排程。
    public List<ReviewTodayItemView> listToday(String userIdRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        Instant now = Instant.now(clock);

        List<NoteQueryService.NoteSummaryView> notes = noteRepository.findAllByUserId(userId);
        for (NoteQueryService.NoteSummaryView note : notes) {
            reviewStateRepository.createInitialScheduleIfMissing(userId, note.id(), now);
        }

        Map<UUID, NoteQueryService.NoteSummaryView> noteById = notes.stream()
            .collect(Collectors.toMap(NoteQueryService.NoteSummaryView::id, Function.identity()));

        List<ReviewTodayItemView> items = reviewStateRepository.findDueByUserId(userId, now).stream()
            .map(reviewState -> toTodayItem(reviewState, noteById))
            .toList();
        return items;
    }

    // 构建 upcoming review 队列。
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
    // 独立读取单条 review 的 prep 视图，单条走 AI 渲染但不影响 today / complete 主链路。
    public ReviewPrepView getPrep(String reviewItemIdRaw, String userIdRaw) {
        ReviewContext context = loadReviewContext(reviewItemIdRaw, userIdRaw);
        ReviewTodayItemView baseItem = toTodayItem(
            context.reviewState(),
            Map.of(context.noteSummary().id(), context.noteSummary())
        );
        ReviewTodayItemView renderedItem = renderPrepItemWithAi(
            context.reviewState().userId(),
            context.reviewState(),
            context.noteSummary(),
            baseItem
        );
        return new ReviewPrepView(
            renderedItem.id(),
            renderedItem.aiRecallSummary(),
            renderedItem.aiReviewKeyPoints(),
            renderedItem.aiExtensionPreview()
        );
    }

    @Transactional
    // 独立读取单条 review 的完成反馈，失败时复用既有 fallback 语义返回可用结果。
    public ReviewFeedbackView getFeedback(String reviewItemIdRaw, String userIdRaw) {
        ReviewContext context = loadReviewContext(reviewItemIdRaw, userIdRaw);
        ReviewStateView target = context.reviewState();
        NoteQueryService.NoteSummaryView noteSummary = context.noteSummary();
        ReviewCompletionStatus completionStatus = target.completionStatus();
        ReviewCompletionReason completionReason = target.completionReason();
        RecallFeedback recallFeedback = new RecallFeedback(target.selfRecallResult(), target.note());

        UUID feedbackRequestId = UUID.randomUUID();
        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("review_request_id", feedbackRequestId);
        traceState.put("user_id", target.userId());
        traceState.put("review_item_id", target.id());
        traceState.put("note_id", target.noteId());
        traceState.put("queue_type", target.queueType().name());
        traceState.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            traceState.put("completion_reason", completionReason.name());
        }
        traceState.put("result", "RUNNING");

        UUID traceId = agentTraceRepository.create(
            target.userId(),
            "REVIEW_AI_FEEDBACK",
            "Render review feedback for " + target.id(),
            "REVIEW_STATE",
            target.id(),
            List.of("review-ai-feedback-worker"),
            traceState
        );

        CompletionFeedback feedback = buildCompletionFeedback(
            target.userId(),
            traceId,
            target,
            noteSummary,
            completionStatus,
            completionReason,
            recallFeedback
        );

        Map<String, Object> completedState = new LinkedHashMap<>(traceState);
        completedState.put("ai_feedback_status", feedback.aiFeedbackStatus());
        completedState.put("extension_suggestion_count", feedback.extensionSuggestions().size());
        completedState.put("follow_up_task_suggestion_present", feedback.followUpTaskSuggestion() != null);
        if ("FAILED".equals(feedback.aiFeedbackStatus())) {
            completedState.put("result", "FAILED");
            agentTraceRepository.markFailed(traceId, "Review AI feedback degraded", completedState);
        } else {
            completedState.put("result", "COMPLETED");
            agentTraceRepository.markCompleted(traceId, "Rendered review feedback", completedState);
        }
        log.info(
            "module=ReviewApplicationService action=review_feedback_trace_persisted trace_id={} user_id={} review_item_id={} result={} ai_feedback_status={}",
            traceId,
            target.userId(),
            target.id(),
            "FAILED".equals(feedback.aiFeedbackStatus()) ? "FAILED" : "COMPLETED",
            feedback.aiFeedbackStatus()
        );
        return new ReviewFeedbackView(
            target.id(),
            feedback.recallFeedbackSummary(),
            feedback.nextReviewHint(),
            feedback.extensionSuggestions(),
            feedback.followUpTaskSuggestion()
        );
    }

    // 独立渲染单条 review 的 prep 视图，prep / today 语义分开，避免把单条读取记成 today query。
    private ReviewTodayItemView renderPrepItemWithAi(UUID userId,
                                                     ReviewStateView target,
                                                     NoteQueryService.NoteSummaryView noteSummary,
                                                     ReviewTodayItemView item) {
        UUID renderRequestId = UUID.randomUUID();
        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("review_request_id", renderRequestId);
        traceState.put("user_id", userId);
        traceState.put("review_item_id", target.id());
        traceState.put("note_id", target.noteId());
        traceState.put("review_count", 1);
        traceState.put("result", "RUNNING");

        UUID traceId = agentTraceRepository.create(
            userId,
            "REVIEW_AI_PREP",
            "Render prep view for review " + target.id(),
            "REVIEW_STATE",
            target.id(),
            List.of("review-ai-prep-worker"),
            traceState
        );
        long startedAt = System.nanoTime();
        log.info(
            "module=ReviewApplicationService action=review_prep_ai_start trace_id={} user_id={} review_item_id={} note_id={} review_count=1 result=RUNNING",
            traceId,
            userId,
            target.id(),
            target.noteId()
        );

        try {
            ReviewAiAssistant.ReviewRenderResult result = reviewAiAssistant.renderTodayItems(new ReviewAiAssistant.ReviewRenderRequest(
                userId,
                traceId,
                List.of(new ReviewAiAssistant.ReviewRenderCandidate(
                    item.id(),
                    item.noteId(),
                    item.title(),
                    item.currentSummary(),
                    item.currentKeyPoints(),
                    item.currentTags(),
                    item.queueType(),
                    item.nextReviewAt(),
                    item.retryAfterHours()
                ))
            ));
            ReviewTodayItemView renderedItem = mergeTodayItem(item, result.itemsByReviewItemId().get(item.id()));
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-prep",
                "COMPLETED",
                reviewPrepToolInput(target),
                reviewPrepToolOutput(renderedItem),
                toLatencyMs(durationMs),
                null,
                null
            );
            userActionEventRepository.append(
                userId,
                "REVIEW_PREP_RENDERED",
                "REVIEW_STATE",
                target.id(),
                traceId,
                Map.of(
                    "review_item_id", target.id(),
                    "review_count", 1,
                    "result", "COMPLETED"
                )
            );
            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("result", "COMPLETED");
            completedState.put("enhanced_review_count", result.itemsByReviewItemId().size());
            agentTraceRepository.markCompleted(
                traceId,
                "Rendered AI prep view for review " + target.id(),
                completedState
            );
            log.info(
                "module=ReviewApplicationService action=review_prep_trace_persisted trace_id={} user_id={} review_item_id={} result=COMPLETED",
                traceId,
                userId,
                target.id()
            );
            log.info(
                "module=ReviewApplicationService action=review_prep_ai_success trace_id={} user_id={} review_item_id={} result=COMPLETED enhanced_review_count={} duration_ms={}",
                traceId,
                userId,
                target.id(),
                result.itemsByReviewItemId().size(),
                durationMs
            );
            return renderedItem;
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-prep",
                "FAILED",
                reviewPrepToolInput(target),
                reviewPrepToolOutput(item),
                toLatencyMs(durationMs),
                "REVIEW_AI_PREP_FAILED",
                exception.getMessage()
            );
            userActionEventRepository.append(
                userId,
                "REVIEW_PREP_DEGRADED",
                "REVIEW_STATE",
                target.id(),
                traceId,
                Map.of(
                    "review_item_id", target.id(),
                    "review_count", 1,
                    "result", "FAILED",
                    "failure_reason", exception.getMessage()
                )
            );
            Map<String, Object> failedState = new LinkedHashMap<>(traceState);
            failedState.put("result", "FAILED");
            failedState.put("failure_reason", exception.getMessage());
            agentTraceRepository.markFailed(traceId, "Review prep degraded", failedState);
            log.info(
                "module=ReviewApplicationService action=review_prep_trace_persisted trace_id={} user_id={} review_item_id={} result=FAILED",
                traceId,
                userId,
                target.id()
            );
            log.warn(
                "module=ReviewApplicationService action=review_prep_ai_fail trace_id={} user_id={} review_item_id={} result=FAILED error_message={} duration_ms={}",
                traceId,
                userId,
                target.id(),
                exception.getMessage(),
                durationMs
            );
            return item;
        }
    }

    // 在 today 路径上补齐 AI recall 视图，失败时只降级字段，不影响主查询结果。
    private List<ReviewTodayItemView> enrichTodayItemsWithAi(UUID userId, List<ReviewTodayItemView> items) {
        if (items.isEmpty()) {
            return items;
        }

        UUID renderRequestId = UUID.randomUUID();
        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("review_request_id", renderRequestId);
        traceState.put("user_id", userId);
        traceState.put("review_count", items.size());
        traceState.put("review_item_ids", items.stream().map(ReviewTodayItemView::id).toList());
        traceState.put("note_ids", items.stream().map(ReviewTodayItemView::noteId).toList());
        traceState.put("result", "RUNNING");

        UUID traceId = agentTraceRepository.create(
            userId,
            "REVIEW_AI_RENDER",
            "Render recall-friendly review cards",
            "REVIEW_TODAY_QUERY",
            renderRequestId,
            List.of("review-ai-render-worker"),
            traceState
        );
        long startedAt = System.nanoTime();
        log.info(
            "module=ReviewApplicationService action=review_ai_render_start trace_id={} user_id={} review_count={} review_item_ids={} note_ids={}",
            traceId,
            userId,
            items.size(),
            items.stream().map(ReviewTodayItemView::id).toList(),
            items.stream().map(ReviewTodayItemView::noteId).toList()
        );

        try {
            ReviewAiAssistant.ReviewRenderResult result = reviewAiAssistant.renderTodayItems(new ReviewAiAssistant.ReviewRenderRequest(
                userId,
                traceId,
                items.stream()
                    .map(item -> new ReviewAiAssistant.ReviewRenderCandidate(
                        item.id(),
                        item.noteId(),
                        item.title(),
                        item.currentSummary(),
                        item.currentKeyPoints(),
                        item.currentTags(),
                        item.queueType(),
                        item.nextReviewAt(),
                        item.retryAfterHours()
                    ))
                    .toList()
            ));
            List<ReviewTodayItemView> enhancedItems = items.stream()
                .map(item -> mergeTodayItem(item, result.itemsByReviewItemId().get(item.id())))
                .toList();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-render",
                "COMPLETED",
                Map.of(
                    "request_type", "REVIEW_RENDER",
                    "review_request_id", renderRequestId,
                    "review_count", items.size()
                ),
                Map.of(
                    "review_count", items.size(),
                    "enhanced_review_count", result.itemsByReviewItemId().size()
                ),
                toLatencyMs(durationMs),
                null,
                null
            );
            userActionEventRepository.append(
                userId,
                "REVIEW_AI_RENDERED",
                "REVIEW_TODAY_QUERY",
                renderRequestId,
                traceId,
                Map.of(
                    "review_count", items.size(),
                    "enhanced_review_count", result.itemsByReviewItemId().size()
                )
            );

            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("result", "COMPLETED");
            completedState.put("enhanced_review_count", result.itemsByReviewItemId().size());
            agentTraceRepository.markCompleted(
                traceId,
                "Rendered AI recall view for " + result.itemsByReviewItemId().size() + " review items",
                completedState
            );
            log.info(
                "module=ReviewApplicationService action=review_ai_render_success trace_id={} user_id={} review_count={} enhanced_review_count={} duration_ms={}",
                traceId,
                userId,
                items.size(),
                result.itemsByReviewItemId().size(),
                durationMs
            );
            return enhancedItems;
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-render",
                "FAILED",
                Map.of(
                    "request_type", "REVIEW_RENDER",
                    "review_request_id", renderRequestId,
                    "review_count", items.size()
                ),
                Map.of("review_count", items.size()),
                toLatencyMs(durationMs),
                "REVIEW_AI_RENDER_FAILED",
                exception.getMessage()
            );
            userActionEventRepository.append(
                userId,
                "REVIEW_AI_RENDER_DEGRADED",
                "REVIEW_TODAY_QUERY",
                renderRequestId,
                traceId,
                Map.of(
                    "review_count", items.size(),
                    "failure_reason", exception.getMessage()
                )
            );
            Map<String, Object> failedState = new LinkedHashMap<>(traceState);
            failedState.put("result", "FAILED");
            failedState.put("failure_reason", exception.getMessage());
            agentTraceRepository.markFailed(traceId, "Review AI render degraded", failedState);
            log.warn(
                "module=ReviewApplicationService action=review_ai_render_fail trace_id={} user_id={} review_count={} error_message={} duration_ms={}",
                traceId,
                userId,
                items.size(),
                exception.getMessage(),
                durationMs
            );
            return items;
        }
    }

    private ReviewTodayItemView mergeTodayItem(ReviewTodayItemView item, ReviewAiAssistant.RenderedReviewItem rendered) {
        if (rendered == null) {
            return item;
        }
        return new ReviewTodayItemView(
            item.id(),
            item.noteId(),
            item.queueType(),
            item.completionStatus(),
            item.completionReason(),
            item.masteryScore(),
            item.nextReviewAt(),
            item.retryAfterHours(),
            item.unfinishedCount(),
            item.title(),
            item.currentSummary(),
            item.currentKeyPoints(),
            item.currentTags(),
            blankToNull(rendered.recallSummary()),
            sanitizeList(rendered.reviewKeyPoints(), 4),
            blankToNull(rendered.extensionPreview())
        );
    }

    @Transactional
    // 完成 review：按队列类型执行状态迁移、派生任务并写 trace/event。
    public ReviewCompletionView complete(String reviewItemIdRaw, CompleteReviewCommand command) {
        UUID reviewItemId = parseUuid(reviewItemIdRaw, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        ReviewCompletionStatus completionStatus = parseStatus(command.completionStatus());
        ReviewCompletionReason completionReason = validateReason(completionStatus, command.completionReason());

        ReviewStateView target = reviewStateRepository.findByIdAndUserId(reviewItemId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_ITEM_NOT_FOUND", "review item not found"));
        RecallFeedback recallFeedback = validateRecallFeedback(target.queueType(), command.selfRecallResult(), command.note());
        long startedAt = System.nanoTime();
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
        if (completionReason != null) {
            traceState.put("completion_reason", completionReason.name());
        }
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
        // Phase 3 先返回主业务结果，AI 反馈改为独立读取，不在 complete 内同步加载。
        CompletionFeedback feedback = CompletionFeedback.notLoaded();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        Map<String, Object> payload = completionPayload(target.noteId(), target.queueType(), completionStatus, completionReason, recallFeedback);
        if (followUpTaskId != null) {
            payload.put("follow_up_task_id", followUpTaskId);
        }
        userActionEventRepository.append(
            userId,
            reviewEventType(completionStatus),
            "REVIEW_STATE",
            reviewItemId,
            traceId,
            payload
        );

        Map<String, Object> toolInputDigest = reviewToolInputDigest(reviewItemId, target.queueType(), completionStatus, completionReason, recallFeedback);
        Map<String, Object> toolOutputDigest = reviewToolOutputDigest(updated, followUpTaskId);
        toolInvocationLogRepository.append(
            userId,
            traceId,
            "review.complete",
            "COMPLETED",
            toolInputDigest,
            toolOutputDigest,
            toLatencyMs(durationMs),
            null,
            null
        );

        Map<String, Object> completionState = new LinkedHashMap<>();
        completionState.put("review_item_id", reviewItemId);
        completionState.put("note_id", target.noteId());
        completionState.put("queue_type", target.queueType().name());
        completionState.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            completionState.put("completion_reason", completionReason.name());
        }
        if (recallFeedback.selfRecallResult() != null) {
            completionState.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        if (recallFeedback.note() != null) {
            completionState.put("note", recallFeedback.note());
        }
        if (followUpTaskId != null) {
            completionState.put("follow_up_task_id", followUpTaskId);
        }
        completionState.put("ai_feedback_status", feedback.aiFeedbackStatus());
        agentTraceRepository.markCompleted(traceId, "Completed review " + reviewItemId, completionState);
        log.info(
            "module=ReviewApplicationService action=review_trace_persisted trace_id={} user_id={} review_item_id={} ai_feedback_status={}",
            traceId,
            userId,
            reviewItemId,
            feedback.aiFeedbackStatus()
        );
        log.info(
            "action=review_complete_success user_id={} review_item_id={} queue_type={} trace_id={} completion_status={} completion_reason={} self_recall_result={} has_note={} ai_feedback_status={} duration_ms={}",
            userId,
            reviewItemId,
            target.queueType(),
            traceId,
            completionStatus,
            completionReason == null ? null : completionReason.name(),
            recallFeedback.selfRecallResult() == null ? null : recallFeedback.selfRecallResult().name(),
            recallFeedback.note() != null,
            feedback.aiFeedbackStatus(),
            durationMs
        );
        return toCompletionView(updated, feedback);
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
            note.currentKeyPoints(),
            note.currentTags(),
            null,
            List.of(),
            null
        );
    }

    private ReviewCompletionView toCompletionView(ReviewStateView reviewState, CompletionFeedback feedback) {
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
            reviewState.masteryScore(),
            feedback.recallFeedbackSummary(),
            feedback.nextReviewHint(),
            feedback.extensionSuggestions(),
            feedback.followUpTaskSuggestion()
        );
    }

    // 读取当前 Note 的解释层摘要，供 review 完成后的 AI 反馈使用。
    private NoteQueryService.NoteSummaryView findNoteSummary(UUID userId, UUID noteId) {
        return noteRepository.findAllByUserId(userId).stream()
            .filter(note -> note.id().equals(noteId))
            .findFirst()
            .orElseGet(() -> new NoteQueryService.NoteSummaryView(
                noteId,
                userId,
                "Note " + noteId,
                null,
                List.of(),
                List.of(),
                null,
                Instant.now(clock)
            ));
    }

    // 读取单条 review 的状态和 note 解释层摘要，给 prep / feedback read 入口复用。
    private ReviewContext loadReviewContext(String reviewItemIdRaw, String userIdRaw) {
        UUID reviewItemId = parseUuid(reviewItemIdRaw, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        ReviewStateView reviewState = reviewStateRepository.findByIdAndUserId(reviewItemId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_ITEM_NOT_FOUND", "review item not found"));
        NoteQueryService.NoteSummaryView noteSummary = findNoteSummary(userId, reviewState.noteId());
        return new ReviewContext(reviewState, noteSummary);
    }

    // 在 review 主状态流转完成后补充 AI 反馈，失败时只降级反馈字段，不回滚主业务。
    private CompletionFeedback buildCompletionFeedback(UUID userId,
                                                       UUID traceId,
                                                       ReviewStateView target,
                                                       NoteQueryService.NoteSummaryView noteSummary,
                                                       ReviewCompletionStatus completionStatus,
                                                       ReviewCompletionReason completionReason,
                                                       RecallFeedback recallFeedback) {
        long startedAt = System.nanoTime();
        log.info(
            "module=ReviewApplicationService action=review_feedback_ai_start trace_id={} user_id={} review_item_id={} note_id={} completion_status={} queue_type={} result=RUNNING",
            traceId,
            userId,
            target.id(),
            target.noteId(),
            completionStatus.name(),
            target.queueType().name()
        );

        try {
            ReviewAiAssistant.ReviewFeedbackResult aiResult = reviewAiAssistant.buildCompletionFeedback(new ReviewAiAssistant.ReviewFeedbackRequest(
                userId,
                traceId,
                target.id(),
                target.noteId(),
                noteSummary.title(),
                noteSummary.currentSummary(),
                noteSummary.currentKeyPoints(),
                noteSummary.currentTags(),
                target.queueType(),
                completionStatus,
                completionReason,
                recallFeedback.selfRecallResult(),
                recallFeedback.note()
            ));
            CompletionFeedback feedback = normalizeCompletionFeedback(
                completionStatus,
                completionReason,
                noteSummary.title(),
                aiResult == null ? new ReviewAiAssistant.ReviewFeedbackResult(null, null, List.of(), null) : aiResult
            );
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-feedback",
                "COMPLETED",
                reviewFeedbackToolInput(target, completionStatus, completionReason, recallFeedback),
                reviewFeedbackToolOutput(feedback),
                toLatencyMs(durationMs),
                null,
                null
            );
            userActionEventRepository.append(
                userId,
                "REVIEW_FEEDBACK_GENERATED",
                "REVIEW_STATE",
                target.id(),
                traceId,
                Map.of(
                    "completion_status", completionStatus.name(),
                    "extension_suggestion_count", feedback.extensionSuggestions().size()
                )
            );
            if (!feedback.extensionSuggestions().isEmpty()) {
                userActionEventRepository.append(
                    userId,
                    "REVIEW_EXTENSION_SUGGESTIONS_GENERATED",
                    "REVIEW_STATE",
                    target.id(),
                    traceId,
                    Map.of(
                        "completion_status", completionStatus.name(),
                        "extension_suggestion_count", feedback.extensionSuggestions().size()
                    )
                );
                log.info(
                    "module=ReviewApplicationService action=review_extension_suggestions_built trace_id={} user_id={} review_item_id={} extension_suggestion_count={}",
                    traceId,
                    userId,
                    target.id(),
                    feedback.extensionSuggestions().size()
                );
            }
            if (feedback.followUpTaskSuggestion() != null) {
                userActionEventRepository.append(
                    userId,
                    "REVIEW_FOLLOW_UP_TASK_SUGGESTED",
                    "REVIEW_STATE",
                    target.id(),
                    traceId,
                    Map.of(
                        "completion_status", completionStatus.name(),
                        "follow_up_task_suggestion", feedback.followUpTaskSuggestion()
                    )
                );
            }
            log.info(
                "module=ReviewApplicationService action=review_follow_up_task_suggestion_decided trace_id={} user_id={} review_item_id={} follow_up_task_suggestion_present={}",
                traceId,
                userId,
                target.id(),
                feedback.followUpTaskSuggestion() != null
            );
            log.info(
                "module=ReviewApplicationService action=review_feedback_ai_success trace_id={} user_id={} review_item_id={} completion_status={} extension_suggestion_count={} result=COMPLETED duration_ms={}",
                traceId,
                userId,
                target.id(),
                completionStatus.name(),
                feedback.extensionSuggestions().size(),
                durationMs
            );
            return feedback;
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            CompletionFeedback fallbackFeedback = fallbackCompletionFeedback(completionStatus, completionReason, noteSummary.title());
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "review.ai-feedback",
                "FAILED",
                reviewFeedbackToolInput(target, completionStatus, completionReason, recallFeedback),
                reviewFeedbackToolOutput(fallbackFeedback),
                toLatencyMs(durationMs),
                "REVIEW_AI_FEEDBACK_FAILED",
                exception.getMessage()
            );
            Map<String, Object> degradedPayload = new LinkedHashMap<>();
            degradedPayload.put("completion_status", completionStatus.name());
            degradedPayload.put("ai_feedback_status", fallbackFeedback.aiFeedbackStatus());
            degradedPayload.put("error_message", exception.getMessage());
            userActionEventRepository.append(
                userId,
                "REVIEW_FEEDBACK_DEGRADED",
                "REVIEW_STATE",
                target.id(),
                traceId,
                degradedPayload
            );
            log.warn(
                "module=ReviewApplicationService action=review_feedback_ai_fail trace_id={} user_id={} review_item_id={} completion_status={} result=FAILED error_message={} duration_ms={}",
                traceId,
                userId,
                target.id(),
                completionStatus.name(),
                exception.getMessage(),
                durationMs
            );
            return fallbackFeedback;
        }
    }

    private CompletionFeedback normalizeCompletionFeedback(ReviewCompletionStatus completionStatus,
                                                           ReviewCompletionReason completionReason,
                                                           String noteTitle,
                                                           ReviewAiAssistant.ReviewFeedbackResult aiResult) {
        String fallbackSummary = fallbackFeedbackSummary(completionStatus, completionReason, noteTitle);
        String fallbackHint = fallbackNextReviewHint(completionStatus, completionReason);
        List<String> fallbackSuggestions = fallbackExtensionSuggestions(completionStatus, completionReason);
        String fallbackFollowUpTaskSuggestion = fallbackFollowUpTaskSuggestion(completionStatus, completionReason, noteTitle);

        List<String> normalizedSuggestions = sanitizeList(aiResult.extensionSuggestions(), 3);
        if (normalizedSuggestions.isEmpty()) {
            normalizedSuggestions = fallbackSuggestions;
        }

        return CompletionFeedback.completed(
            blankToNull(aiResult.recallFeedbackSummary()) == null ? fallbackSummary : aiResult.recallFeedbackSummary().trim(),
            blankToNull(aiResult.nextReviewHint()) == null ? fallbackHint : aiResult.nextReviewHint().trim(),
            normalizedSuggestions,
            blankToNull(aiResult.followUpTaskSuggestion()) == null ? fallbackFollowUpTaskSuggestion : aiResult.followUpTaskSuggestion().trim()
        );
    }

    private CompletionFeedback fallbackCompletionFeedback(ReviewCompletionStatus completionStatus,
                                                          ReviewCompletionReason completionReason,
                                                          String noteTitle) {
        return new CompletionFeedback(
            "FAILED",
            fallbackFeedbackSummary(completionStatus, completionReason, noteTitle),
            fallbackNextReviewHint(completionStatus, completionReason),
            fallbackExtensionSuggestions(completionStatus, completionReason),
            fallbackFollowUpTaskSuggestion(completionStatus, completionReason, noteTitle)
        );
    }

    private Map<String, Object> reviewFeedbackToolInput(ReviewStateView target,
                                                        ReviewCompletionStatus completionStatus,
                                                        ReviewCompletionReason completionReason,
                                                        RecallFeedback recallFeedback) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("request_type", "REVIEW_FEEDBACK");
        digest.put("review_item_id", target.id());
        digest.put("note_id", target.noteId());
        digest.put("queue_type", target.queueType().name());
        digest.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            digest.put("completion_reason", completionReason.name());
        }
        if (recallFeedback.selfRecallResult() != null) {
            digest.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        digest.put("has_note", recallFeedback.note() != null);
        return digest;
    }

    private Map<String, Object> reviewFeedbackToolOutput(CompletionFeedback feedback) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("ai_feedback_status", feedback.aiFeedbackStatus());
        digest.put("has_recall_feedback_summary", feedback.recallFeedbackSummary() != null);
        digest.put("has_next_review_hint", feedback.nextReviewHint() != null);
        digest.put("extension_suggestion_count", feedback.extensionSuggestions().size());
        digest.put("follow_up_task_suggestion_present", feedback.followUpTaskSuggestion() != null);
        return digest;
    }

    private Map<String, Object> reviewPrepToolInput(ReviewStateView target) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("request_type", "REVIEW_PREP");
        digest.put("review_item_id", target.id());
        digest.put("note_id", target.noteId());
        digest.put("queue_type", target.queueType().name());
        digest.put("review_count", 1);
        return digest;
    }

    private Map<String, Object> reviewPrepToolOutput(ReviewTodayItemView item) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("review_item_id", item.id());
        digest.put("has_ai_recall_summary", item.aiRecallSummary() != null);
        digest.put("ai_review_key_point_count", item.aiReviewKeyPoints().size());
        digest.put("has_ai_extension_preview", item.aiExtensionPreview() != null);
        return digest;
    }

    private Map<String, Object> completionPayload(UUID noteId,
                                                  ReviewQueueType queueType,
                                                  ReviewCompletionStatus completionStatus,
                                                  ReviewCompletionReason completionReason,
                                                  RecallFeedback recallFeedback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("note_id", noteId);
        payload.put("queue_type", queueType.name());
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

    private String reviewEventType(ReviewCompletionStatus completionStatus) {
        return switch (completionStatus) {
            case COMPLETED -> "REVIEW_COMPLETED";
            case PARTIAL -> "REVIEW_PARTIAL";
            case NOT_STARTED -> "REVIEW_NOT_STARTED";
            case ABANDONED -> "REVIEW_ABANDONED";
        };
    }

    private Map<String, Object> reviewToolInputDigest(UUID reviewItemId,
                                                      ReviewQueueType queueType,
                                                      ReviewCompletionStatus completionStatus,
                                                      ReviewCompletionReason completionReason,
                                                      RecallFeedback recallFeedback) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("review_item_id", reviewItemId);
        digest.put("queue_type", queueType.name());
        digest.put("completion_status", completionStatus.name());
        if (completionReason != null) {
            digest.put("completion_reason", completionReason.name());
        }
        if (recallFeedback.selfRecallResult() != null) {
            digest.put("self_recall_result", recallFeedback.selfRecallResult().name());
        }
        digest.put("has_note", recallFeedback.note() != null);
        return digest;
    }

    private Map<String, Object> reviewToolOutputDigest(ReviewStateView updated, UUID followUpTaskId) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("review_item_id", updated.id());
        digest.put("completion_status", updated.completionStatus().name());
        digest.put("queue_type", updated.queueType().name());
        digest.put("next_review_at", updated.nextReviewAt());
        digest.put("retry_after_hours", updated.retryAfterHours());
        digest.put("unfinished_count", updated.unfinishedCount());
        digest.put("mastery_score", updated.masteryScore());
        if (followUpTaskId != null) {
            digest.put("follow_up_task_id", followUpTaskId);
        }
        return digest;
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
                                 com.noteops.agent.service.task.TaskApplicationService.TaskView existing,
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
        com.noteops.agent.service.task.TaskApplicationService.TaskView task = taskRepository.create(
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

    private String fallbackFeedbackSummary(ReviewCompletionStatus completionStatus,
                                           ReviewCompletionReason completionReason,
                                           String noteTitle) {
        return switch (completionStatus) {
            case COMPLETED -> "本次对 " + noteTitle + " 的回忆已完成，建议趁记忆还清晰时再做一次简短复述。";
            case PARTIAL -> "本次复习只覆盖了部分内容，重点缺口需要回到关键点继续补齐。";
            case NOT_STARTED -> "这次还没有真正开始复习，先从最低门槛的一步重新进入会更稳。";
            case ABANDONED -> "这次复习已放弃%s，后续建议先降低难度再重新进入。"
                .formatted(completionReason == null ? "" : "，原因是 " + completionReason.name());
        };
    }

    private String fallbackNextReviewHint(ReviewCompletionStatus completionStatus,
                                          ReviewCompletionReason completionReason) {
        return switch (completionStatus) {
            case COMPLETED -> "下次开始前，先用一句话复述当前摘要，再检查自己是否还能说出两个关键点。";
            case PARTIAL -> "下一次先只盯住最难回忆的 1 到 2 个关键点，不要同时补全部内容。";
            case NOT_STARTED -> "下一次先读当前摘要和第一条关键点，用 2 分钟完成一次最小回忆。";
            case ABANDONED -> completionReason == ReviewCompletionReason.TOO_HARD
                ? "下一次先把内容拆成更小片段，只处理最容易回忆的一段。"
                : "下一次先降低目标，只要求重新进入而不是一次完成。";
        };
    }

    private List<String> fallbackExtensionSuggestions(ReviewCompletionStatus completionStatus,
                                                      ReviewCompletionReason completionReason) {
        return switch (completionStatus) {
            case COMPLETED -> List.of("快速复述一次当前摘要", "抽查两条关键点是否还能独立说出");
            case PARTIAL -> List.of("回看当前关键点里最模糊的部分", "补一条简短自我说明，写下卡住的位置");
            case NOT_STARTED -> List.of("只阅读当前摘要", "先尝试说出一条关键点");
            case ABANDONED -> completionReason == ReviewCompletionReason.TIME_LIMIT
                ? List.of("安排一个更短的复习窗口", "只保留一个最重要的回忆目标")
                : List.of("降低本次复习目标", "优先回到最熟悉的关键点");
        };
    }

    private String fallbackFollowUpTaskSuggestion(ReviewCompletionStatus completionStatus,
                                                  ReviewCompletionReason completionReason,
                                                  String noteTitle) {
        return switch (completionStatus) {
            case COMPLETED -> null;
            case PARTIAL -> "可考虑创建一条 follow-up task：回看 " + noteTitle + " 中最模糊的关键点。";
            case NOT_STARTED -> "可考虑创建一条低门槛 follow-up task：重新开始一次 2 分钟复习。";
            case ABANDONED -> completionReason == ReviewCompletionReason.TOO_HARD
                ? "可考虑创建一条 follow-up task：把复习内容拆成更小片段再试。"
                : "可考虑创建一条 follow-up task：为这条复习预留更合适的时间窗口。";
        };
    }

    private int toLatencyMs(long durationMs) {
        if (durationMs > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) durationMs;
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

    private List<String> sanitizeList(List<String> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized == null) {
                continue;
            }
            sanitized.add(normalized);
            if (sanitized.size() >= maxSize) {
                break;
            }
        }
        return List.copyOf(sanitized);
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
        List<String> currentKeyPoints,
        List<String> currentTags,
        String aiRecallSummary,
        List<String> aiReviewKeyPoints,
        String aiExtensionPreview
    ) {
    }

    public record ReviewPrepView(
        UUID reviewItemId,
        String aiRecallSummary,
        List<String> aiReviewKeyPoints,
        String aiExtensionPreview
    ) {
    }

    public record ReviewFeedbackView(
        UUID reviewItemId,
        String recallFeedbackSummary,
        String nextReviewHint,
        List<String> extensionSuggestions,
        String followUpTaskSuggestion
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
        BigDecimal masteryScore,
        String recallFeedbackSummary,
        String nextReviewHint,
        List<String> extensionSuggestions,
        String followUpTaskSuggestion
    ) {
    }

    private record RecallFeedback(ReviewSelfRecallResult selfRecallResult, String note) {
    }

    private record FollowUpTaskSpec(String title, String description, Instant dueAt, int priority) {
    }

    private record ReviewContext(ReviewStateView reviewState, NoteQueryService.NoteSummaryView noteSummary) {
    }

    private record CompletionFeedback(
        String aiFeedbackStatus,
        String recallFeedbackSummary,
        String nextReviewHint,
        List<String> extensionSuggestions,
        String followUpTaskSuggestion
    ) {
        private CompletionFeedback {
            extensionSuggestions = extensionSuggestions == null ? List.of() : List.copyOf(extensionSuggestions);
        }

        private static CompletionFeedback notLoaded() {
            return new CompletionFeedback("NOT_LOADED", null, null, List.of(), null);
        }

        private static CompletionFeedback completed(String recallFeedbackSummary,
                                                   String nextReviewHint,
                                                   List<String> extensionSuggestions,
                                                   String followUpTaskSuggestion) {
            return new CompletionFeedback(
                "COMPLETED",
                recallFeedbackSummary,
                nextReviewHint,
                extensionSuggestions,
                followUpTaskSuggestion
            );
        }

        private static CompletionFeedback failed() {
            return new CompletionFeedback("FAILED", null, null, List.of(), null);
        }
    }
}
