package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrendActionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TrendActionApplicationService.class);
    private static final String ACTION_TOOL_NAME = "trend.item.action";
    private static final String NOTE_CONVERSION_TOOL_NAME = "trend.note.convert";
    private static final String IDEA_CONVERSION_TOOL_NAME = "trend.idea.convert";

    private final TrendItemRepository trendItemRepository;
    private final TrendNoteConversionService trendNoteConversionService;
    private final TrendIdeaConversionService trendIdeaConversionService;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    public TrendActionApplicationService(TrendItemRepository trendItemRepository,
                                         AgentTraceRepository agentTraceRepository,
                                         ToolInvocationLogRepository toolInvocationLogRepository,
                                         UserActionEventRepository userActionEventRepository) {
        this(trendItemRepository, null, null, agentTraceRepository, toolInvocationLogRepository, userActionEventRepository);
    }

    public TrendActionApplicationService(TrendItemRepository trendItemRepository,
                                         TrendNoteConversionService trendNoteConversionService,
                                         AgentTraceRepository agentTraceRepository,
                                         ToolInvocationLogRepository toolInvocationLogRepository,
                                         UserActionEventRepository userActionEventRepository) {
        this(trendItemRepository, trendNoteConversionService, null, agentTraceRepository, toolInvocationLogRepository, userActionEventRepository);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TrendActionApplicationService(TrendItemRepository trendItemRepository,
                                         TrendNoteConversionService trendNoteConversionService,
                                         TrendIdeaConversionService trendIdeaConversionService,
                                         AgentTraceRepository agentTraceRepository,
                                         ToolInvocationLogRepository toolInvocationLogRepository,
                                         UserActionEventRepository userActionEventRepository) {
        this.trendItemRepository = trendItemRepository;
        this.trendNoteConversionService = trendNoteConversionService;
        this.trendIdeaConversionService = trendIdeaConversionService;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ActionResult act(ActionCommand command) {
        long startedAt = System.nanoTime();
        UUID traceId = parseUuid(command.traceId(), "INVALID_TRACE_ID", "trace_id must be a valid UUID", null);
        String traceIdText = traceId.toString();
        log.info(
            "module=TrendActionApplicationService action=trend_action_start trace_id={} user_id={} trend_item_id={} action={} result=RUNNING",
            traceIdText,
            command.userId(),
            command.trendItemId(),
            command.action()
        );

        try {
            UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID", traceIdText);
            UUID trendItemId = parseUuid(command.trendItemId(), "INVALID_TREND_ITEM_ID", "trend_item_id must be a valid UUID", traceIdText);
            TrendActionType action = parseAction(command.action(), traceIdText);

            if (action == TrendActionType.SAVE_AS_NOTE) {
                if (trendNoteConversionService == null) {
                    throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "TREND_NOTE_CONVERSION_FAILED",
                        "trend note conversion service is unavailable",
                        traceIdText
                    );
                }
                TrendNoteConversionService.SaveAsNoteResult conversionResult = trendNoteConversionService.saveAsNote(
                    new TrendNoteConversionService.SaveAsNoteCommand(
                        trendItemId,
                        userId,
                        traceId,
                        blankToNull(command.operatorNote())
                    )
                );
                log.info(
                    "module=TrendActionApplicationService action=trend_action_success trace_id={} user_id={} trend_item_id={} action={} converted_note_id={} result=COMPLETED duration_ms={}",
                    traceIdText,
                    userId,
                    conversionResult.trendItem().id(),
                    action.name(),
                    conversionResult.trendItem().convertedNoteId(),
                    durationMs(startedAt)
                );
                return new ActionResult(traceIdText, conversionResult.trendItem());
            }

            if (action == TrendActionType.PROMOTE_TO_IDEA) {
                if (trendIdeaConversionService == null) {
                    throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "TREND_IDEA_CONVERSION_FAILED",
                        "trend idea conversion service is unavailable",
                        traceIdText
                    );
                }
                TrendIdeaConversionService.PromoteToIdeaResult conversionResult = trendIdeaConversionService.promoteToIdea(
                    new TrendIdeaConversionService.PromoteToIdeaCommand(
                        trendItemId,
                        userId,
                        traceId,
                        blankToNull(command.operatorNote())
                    )
                );
                log.info(
                    "module=TrendActionApplicationService action=trend_action_success trace_id={} user_id={} trend_item_id={} action={} converted_idea_id={} result=COMPLETED duration_ms={}",
                    traceIdText,
                    userId,
                    conversionResult.trendItem().id(),
                    action.name(),
                    conversionResult.trendItem().convertedIdeaId(),
                    durationMs(startedAt)
                );
                return new ActionResult(traceIdText, conversionResult.trendItem());
            }

            Map<String, Object> traceState = new LinkedHashMap<>();
            traceState.put("trend_item_id", command.trendItemId());
            traceState.put("user_id", userId.toString());
            traceState.put("action", command.action());
            traceState.put("operator_note", blankToNull(command.operatorNote()));

            agentTraceRepository.create(
                traceId,
                userId,
                "TREND_ACTION",
                "Process trend action",
                "TREND_ACTION",
                null,
                List.of("trend-action-worker"),
                traceState
            );

            Map<String, Object> inputDigest = new LinkedHashMap<>();
            inputDigest.put("trend_item_id", command.trendItemId());
            inputDigest.put("user_id", userId.toString());
            inputDigest.put("action", command.action());
            inputDigest.put("operator_note", blankToNull(command.operatorNote()));

            toolInvocationLogRepository.append(
                userId,
                traceId,
                ACTION_TOOL_NAME,
                "STARTED",
                inputDigest,
                Map.of(),
                null,
                null,
                null
            );

            if (action != TrendActionType.IGNORE) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TREND_ACTION_DEFERRED",
                    action.name() + " is deferred to Step 4.6",
                    traceIdText
                );
            }

            TrendItemRepository.TrendItemRecord trendItem = trendItemRepository.findByIdAndUserId(trendItemId, userId)
                .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TREND_ITEM_NOT_FOUND",
                    "trend item not found",
                    traceIdText
                ));
            validateActionable(trendItem, traceIdText);
            traceState.put("trend_item_id", trendItem.id().toString());
            traceState.put("status", trendItem.status().name());
            traceState.put("source_type", trendItem.sourceType().name());

            TrendActionType preservedSuggestedAction = trendItem.suggestedAction();
            TrendItemRepository.TrendItemRecord updated = trendItemRepository.updateStatus(
                trendItem.id(),
                userId,
                TrendItemStatus.IGNORED,
                preservedSuggestedAction,
                null,
                null
            );

            Map<String, Object> outputDigest = new LinkedHashMap<>();
            outputDigest.put("trend_item_id", updated.id().toString());
            outputDigest.put("status", updated.status().name());
            outputDigest.put("suggested_action", updated.suggestedAction() == null ? null : updated.suggestedAction().name());
            toolInvocationLogRepository.append(
                userId,
                traceId,
                ACTION_TOOL_NAME,
                "COMPLETED",
                inputDigest,
                outputDigest,
                durationMs(startedAt),
                null,
                null
            );

            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("trend_item_id", updated.id().toString());
            eventPayload.put("action", action.name());
            eventPayload.put("status", updated.status().name());
            eventPayload.put("suggested_action", updated.suggestedAction() == null ? null : updated.suggestedAction().name());
            eventPayload.put("operator_note", blankToNull(command.operatorNote()));
            eventPayload.put("source_type", updated.sourceType().name());
            eventPayload.put("source_item_key", updated.sourceItemKey());
            userActionEventRepository.append(
                userId,
                "TREND_ITEM_IGNORED",
                "TREND_ITEM",
                updated.id(),
                traceId,
                eventPayload
            );

            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("result", "IGNORED");
            completedState.put("suggested_action", updated.suggestedAction() == null ? null : updated.suggestedAction().name());
            agentTraceRepository.markCompleted(
                traceId,
                "Trend item ignored",
                completedState
            );

            log.info(
                "module=TrendActionApplicationService action=trend_action_success trace_id={} user_id={} trend_item_id={} action={} result=COMPLETED duration_ms={}",
                traceIdText,
                userId,
                updated.id(),
                action.name(),
                durationMs(startedAt)
            );

            return new ActionResult(traceIdText, updated);
        } catch (ApiException exception) {
            if (isSaveAsNoteAction(command.action())) {
                markSaveAsNoteFailure(traceId, command, traceStateForFailure(command, exception), startedAt, exception);
            } else if (isPromoteToIdeaAction(command.action())) {
                markPromoteToIdeaFailure(traceId, command, traceStateForFailure(command, exception), startedAt, exception);
            } else {
                markFailedTrace(traceId, command, traceStateForFailure(command, exception), startedAt, exception);
            }
            throw exception.traceId() == null
                ? new ApiException(exception.httpStatus(), exception.errorCode(), exception.getMessage(), traceIdText)
                : exception;
        } catch (Exception exception) {
            ApiException apiException = isSaveAsNoteAction(command.action())
                ? new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TREND_NOTE_CONVERSION_FAILED",
                    exception.getMessage() == null ? "trend note conversion failed" : exception.getMessage(),
                    traceIdText
                )
                : isPromoteToIdeaAction(command.action())
                ? new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TREND_IDEA_CONVERSION_FAILED",
                    exception.getMessage() == null ? "trend idea conversion failed" : exception.getMessage(),
                    traceIdText
                )
                : new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TREND_ACTION_FAILED",
                    exception.getMessage() == null ? "trend action failed" : exception.getMessage(),
                    traceIdText
                );
            if (isSaveAsNoteAction(command.action())) {
                markSaveAsNoteFailure(traceId, command, traceStateForFailure(command, apiException), startedAt, apiException);
            } else if (isPromoteToIdeaAction(command.action())) {
                markPromoteToIdeaFailure(traceId, command, traceStateForFailure(command, apiException), startedAt, apiException);
            } else {
                markFailedTrace(traceId, command, traceStateForFailure(command, apiException), startedAt, apiException);
            }
            throw apiException;
        }
    }

    private void validateActionable(TrendItemRepository.TrendItemRecord trendItem, String traceId) {
        if (trendItem.status() != TrendItemStatus.ANALYZED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "TREND_ITEM_NOT_ACTIONABLE",
                "trend item is not in actionable inbox state",
                traceId
            );
        }
    }

    private TrendActionType parseAction(String rawValue, String traceId) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TREND_ACTION", "action must be provided", traceId);
        }
        try {
            return TrendActionType.valueOf(rawValue.trim());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TREND_ACTION", "action must be a valid trend action", traceId);
        }
    }

    private UUID parseUuid(String rawValue, String errorCode, String message, String traceId) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message, traceId);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private void markFailedTrace(UUID traceId,
                                 ActionCommand command,
                                 Map<String, Object> failedState,
                                 long startedAt,
                                 ApiException exception) {
        UUID userId;
        try {
            userId = UUID.fromString(command.userId());
        } catch (Exception ignore) {
            userId = null;
        }
        if (userId != null) {
            toolInvocationLogRepository.append(
                userId,
                traceId,
                ACTION_TOOL_NAME,
                "FAILED",
                failedState,
                Map.of(),
                durationMs(startedAt),
                exception.errorCode(),
                exception.getMessage()
            );
        }
        agentTraceRepository.markFailed(
            traceId,
            exception.getMessage(),
            failedState
        );
        log.warn(
            "module=TrendActionApplicationService action=trend_action_fail trace_id={} user_id={} trend_item_id={} action={} result=FAILED duration_ms={} error_code={} error_message={}",
            traceId,
            command.userId(),
            command.trendItemId(),
            command.action(),
            durationMs(startedAt),
            exception.errorCode(),
            exception.getMessage()
        );
    }

    private void markSaveAsNoteFailure(UUID traceId,
                                       ActionCommand command,
                                       Map<String, Object> failedState,
                                       long startedAt,
                                       ApiException exception) {
        UUID userId;
        UUID trendItemId;
        try {
            userId = UUID.fromString(command.userId());
        } catch (Exception ignore) {
            userId = null;
        }
        try {
            trendItemId = UUID.fromString(command.trendItemId());
        } catch (Exception ignore) {
            trendItemId = null;
        }
        if (userId != null) {
            agentTraceRepository.create(
                traceId,
                userId,
                "TREND_ACTION",
                "Convert trend item to note",
                "TREND_ITEM",
                trendItemId,
                List.of("trend-note-conversion-worker"),
                failedState
            );
            toolInvocationLogRepository.append(
                userId,
                traceId,
                NOTE_CONVERSION_TOOL_NAME,
                "FAILED",
                failedState,
                Map.of(),
                durationMs(startedAt),
                exception.errorCode(),
                exception.getMessage()
            );
            agentTraceRepository.markFailed(
                traceId,
                exception.getMessage(),
                failedState
            );
        }
        log.warn(
            "module=TrendActionApplicationService action=trend_note_convert_fail trace_id={} user_id={} trend_item_id={} action={} result=FAILED duration_ms={} error_code={} error_message={}",
            traceId,
            command.userId(),
            command.trendItemId(),
            command.action(),
            durationMs(startedAt),
            exception.errorCode(),
            exception.getMessage()
        );
    }

    private void markPromoteToIdeaFailure(UUID traceId,
                                         ActionCommand command,
                                         Map<String, Object> failedState,
                                         long startedAt,
                                         ApiException exception) {
        UUID userId;
        UUID trendItemId;
        try {
            userId = UUID.fromString(command.userId());
        } catch (Exception ignore) {
            userId = null;
        }
        try {
            trendItemId = UUID.fromString(command.trendItemId());
        } catch (Exception ignore) {
            trendItemId = null;
        }
        if (userId != null) {
            agentTraceRepository.create(
                traceId,
                userId,
                "TREND_ACTION",
                "Convert trend item to idea",
                "TREND_ITEM",
                trendItemId,
                List.of("trend-idea-conversion-worker"),
                failedState
            );
            toolInvocationLogRepository.append(
                userId,
                traceId,
                IDEA_CONVERSION_TOOL_NAME,
                "FAILED",
                failedState,
                Map.of(),
                durationMs(startedAt),
                exception.errorCode(),
                exception.getMessage()
            );
            agentTraceRepository.markFailed(
                traceId,
                exception.getMessage(),
                failedState
            );
        }
        log.warn(
            "module=TrendActionApplicationService action=trend_idea_convert_fail trace_id={} user_id={} trend_item_id={} action={} result=FAILED duration_ms={} error_code={} error_message={}",
            traceId,
            command.userId(),
            command.trendItemId(),
            command.action(),
            durationMs(startedAt),
            exception.errorCode(),
            exception.getMessage()
        );
    }

    private Map<String, Object> traceStateForFailure(ActionCommand command, ApiException exception) {
        Map<String, Object> failedState = new LinkedHashMap<>();
        failedState.put("trend_item_id", command.trendItemId());
        failedState.put("user_id", command.userId());
        failedState.put("action", command.action());
        failedState.put("operator_note", blankToNull(command.operatorNote()));
        failedState.put("result", "FAILED");
        failedState.put("error_code", exception.errorCode());
        failedState.put("error_message", exception.getMessage());
        return failedState;
    }

    private boolean isSaveAsNoteAction(String rawAction) {
        return rawAction != null && TrendActionType.SAVE_AS_NOTE.name().equals(rawAction.trim());
    }

    private boolean isPromoteToIdeaAction(String rawAction) {
        return rawAction != null && TrendActionType.PROMOTE_TO_IDEA.name().equals(rawAction.trim());
    }

    public record ActionCommand(
        String trendItemId,
        String userId,
        String action,
        String operatorNote,
        String traceId
    ) {
    }

    public record ActionResult(
        String traceId,
        TrendItemRepository.TrendItemRecord trendItem
    ) {
    }
}
