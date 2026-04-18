package com.noteops.agent.service.sync;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.sync.SyncActionReceiptRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.review.ReviewApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SyncActionsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SyncActionsApplicationService.class);
    private static final int MAX_ACTIONS_PER_REQUEST = 200;
    private static final String SUPPORTED_ACTION_TYPE = "REVIEW_COMPLETE";
    private static final String SUPPORTED_ENTITY_TYPE = "REVIEW_STATE";
    private static final String DEFAULT_SYNC_ERROR_CODE = "SYNC_ACTIONS_APPLY_FAILED";
    private static final String ACTION_PROCESSING_STATUS = "PROCESSING";
    private static final String ACTION_ACCEPTED_STATUS = "ACCEPTED";
    private static final String ACTION_REJECTED_STATUS = "REJECTED";
    private static final String ACTION_IN_PROGRESS_ERROR_CODE = "SYNC_ACTION_IN_PROGRESS";
    private static final String ACTION_IN_PROGRESS_ERROR_MESSAGE = "offline action is currently being processed";

    private final SyncActionReceiptRepository syncActionReceiptRepository;
    private final ReviewApplicationService reviewApplicationService;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final Clock clock;

    @Autowired
    public SyncActionsApplicationService(SyncActionReceiptRepository syncActionReceiptRepository,
                                         ReviewApplicationService reviewApplicationService,
                                         AgentTraceRepository agentTraceRepository,
                                         UserActionEventRepository userActionEventRepository) {
        this(syncActionReceiptRepository, reviewApplicationService, agentTraceRepository, userActionEventRepository, Clock.systemUTC());
    }

    SyncActionsApplicationService(SyncActionReceiptRepository syncActionReceiptRepository,
                                  ReviewApplicationService reviewApplicationService,
                                  AgentTraceRepository agentTraceRepository,
                                  UserActionEventRepository userActionEventRepository,
                                  Clock clock) {
        this.syncActionReceiptRepository = syncActionReceiptRepository;
        this.reviewApplicationService = reviewApplicationService;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.clock = clock;
    }

    @Transactional
    public SyncActionsResult apply(SyncActionsCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        String clientId = normalizeRequired(command.clientId(), "INVALID_CLIENT_ID", "client_id must be provided");
        List<SyncActionCommand> actions = normalizeActions(command.actions());

        UUID traceId = agentTraceRepository.create(
            userId,
            "SYNC_ACTIONS_APPLY",
            "Apply offline sync actions",
            "SYNC_ACTION_BATCH",
            null,
            List.of("sync-actions-worker"),
            Map.of(
                "client_id", clientId,
                "action_count", actions.size()
            )
        );
        String traceIdText = traceId.toString();
        long startedAt = System.nanoTime();
        log.info(
            "module=SyncActionsApplicationService action=sync_actions_apply_start path=/api/v1/sync/actions trace_id={} user_id={} client_id={} action_count={} result=RUNNING",
            traceIdText,
            userId,
            clientId,
            actions.size()
        );

        List<AcceptedActionView> accepted = new ArrayList<>();
        List<RejectedActionView> rejected = new ArrayList<>();

        try {
            for (SyncActionCommand action : actions) {
                SyncActionOutcome outcome = applySingleAction(userId, clientId, traceId, action);
                if (outcome.accepted() != null) {
                    accepted.add(outcome.accepted());
                } else {
                    rejected.add(outcome.rejected());
                }
            }

            Map<String, Object> completedState = new LinkedHashMap<>();
            completedState.put("client_id", clientId);
            completedState.put("action_count", actions.size());
            completedState.put("accepted_count", accepted.size());
            completedState.put("rejected_count", rejected.size());
            agentTraceRepository.markCompleted(traceId, "Applied sync actions", completedState);

            int durationMs = durationMs(startedAt);
            log.info(
                "module=SyncActionsApplicationService action=sync_actions_apply_success path=/api/v1/sync/actions trace_id={} user_id={} client_id={} action=sync_actions_apply result=COMPLETED duration_ms={} accepted_count={} rejected_count={}",
                traceIdText,
                userId,
                clientId,
                durationMs,
                accepted.size(),
                rejected.size()
            );

            return new SyncActionsResult(
                List.copyOf(accepted),
                List.copyOf(rejected),
                traceIdText,
                Instant.now(clock)
            );
        } catch (RuntimeException exception) {
            String errorCode = resolveErrorCode(exception);
            String errorMessage = resolveErrorMessage(exception);
            Map<String, Object> failedState = new LinkedHashMap<>();
            failedState.put("client_id", clientId);
            failedState.put("action_count", actions.size());
            failedState.put("accepted_count", accepted.size());
            failedState.put("rejected_count", rejected.size());
            failedState.put("error_code", errorCode);
            failedState.put("error_message", errorMessage);
            agentTraceRepository.markFailed(traceId, "Failed to apply sync actions", failedState);

            int durationMs = durationMs(startedAt);
            log.error(
                "module=SyncActionsApplicationService action=sync_actions_apply_fail path=/api/v1/sync/actions trace_id={} user_id={} client_id={} action=sync_actions_apply result=FAILED duration_ms={} accepted_count={} rejected_count={} error_code={} error_message={}",
                traceIdText,
                userId,
                clientId,
                durationMs,
                accepted.size(),
                rejected.size(),
                errorCode,
                errorMessage
            );

            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, DEFAULT_SYNC_ERROR_CODE, "sync actions apply failed", traceIdText);
        }
    }

    private SyncActionOutcome applySingleAction(UUID userId,
                                                String clientId,
                                                UUID traceId,
                                                SyncActionCommand action) {
        String rawOfflineActionId = normalizeOptional(action.offlineActionId());
        boolean reserved = false;
        String actionType = normalizeOptional(action.actionType());
        String entityType = normalizeOptional(action.entityType());
        UUID entityId = parseEntityIdQuietly(action.entityId());
        Instant occurredAt = parseOccurredAtOrNull(action.occurredAt());
        Map<String, Object> payload = action.payload() == null ? Map.of() : action.payload();
        try {
            String offlineActionId = normalizeRequired(action.offlineActionId(), "INVALID_OFFLINE_ACTION_ID", "offline_action_id must be provided");
            actionType = normalizeRequired(action.actionType(), "INVALID_SYNC_ACTION_TYPE", "action_type must be provided");
            entityType = normalizeOptional(action.entityType());
            entityId = parseEntityId(action.entityId());
            occurredAt = parseOccurredAt(action.occurredAt());

            SyncActionReceiptRepository.SyncActionReservation reservation = syncActionReceiptRepository.reserveProcessing(
                new SyncActionReceiptRepository.SyncActionReceiptReserveCommand(
                    userId,
                    clientId,
                    offlineActionId,
                    actionType,
                    entityType,
                    entityId,
                    traceId,
                    payload,
                    occurredAt
                )
            );
            if (!reservation.reserved()) {
                SyncActionReceiptRepository.SyncActionReceiptRecord existing = reservation.record();
                if (ACTION_PROCESSING_STATUS.equals(existing.status())) {
                    return new SyncActionOutcome(
                        null,
                        new RejectedActionView(
                            existing.offlineActionId(),
                            existing.actionType(),
                            existing.entityType(),
                            existing.entityId(),
                            ACTION_IN_PROGRESS_ERROR_CODE,
                            ACTION_IN_PROGRESS_ERROR_MESSAGE,
                            true,
                            true
                        )
                    );
                }
                return outcomeFromReceipt(existing, true);
            }
            reserved = true;

            applySupportedAction(userId, actionType, entityType, entityId, payload);
            SyncActionReceiptRepository.SyncActionReceiptRecord saved = syncActionReceiptRepository.updateOutcome(
                new SyncActionReceiptRepository.SyncActionReceiptOutcomeCommand(
                    userId,
                    clientId,
                    offlineActionId,
                    traceId,
                    ACTION_ACCEPTED_STATUS,
                    null,
                    null
                )
            );
            appendSyncEvent(userId, traceId, "OFFLINE_ACTION_SYNC_ACCEPTED", saved, true, null, null);
            return outcomeFromReceipt(saved, false);
        } catch (ApiException exception) {
            String errorCode = resolveErrorCode(exception);
            String errorMessage = resolveErrorMessage(exception);
            String rejectedActionType = actionType == null ? "UNKNOWN" : actionType;
            boolean retryable = isRetryable(exception);
            if (rawOfflineActionId == null || !reserved) {
                return new SyncActionOutcome(
                    null,
                    new RejectedActionView(
                        null,
                        rejectedActionType,
                        entityType,
                        entityId,
                        errorCode,
                        errorMessage,
                        retryable,
                        false
                    )
                );
            }
            if (retryable) {
                tryReleaseReservation(userId, clientId, rawOfflineActionId);
                throw exception;
            }
            SyncActionReceiptRepository.SyncActionReceiptRecord saved = syncActionReceiptRepository.updateOutcome(
                new SyncActionReceiptRepository.SyncActionReceiptOutcomeCommand(
                    userId,
                    clientId,
                    rawOfflineActionId,
                    traceId,
                    ACTION_REJECTED_STATUS,
                    errorCode,
                    errorMessage
                )
            );
            appendSyncEvent(userId, traceId, "OFFLINE_ACTION_SYNC_REJECTED", saved, false, errorCode, errorMessage);
            return new SyncActionOutcome(
                null,
                new RejectedActionView(
                    saved.offlineActionId(),
                    rejectedActionType,
                    saved.entityType(),
                    saved.entityId(),
                    errorCode,
                    errorMessage,
                    false,
                    false
                )
            );
        } catch (RuntimeException exception) {
            if (rawOfflineActionId != null) {
                tryReleaseReservation(userId, clientId, rawOfflineActionId);
            }
            throw exception;
        }
    }

    private SyncActionOutcome outcomeFromReceipt(SyncActionReceiptRepository.SyncActionReceiptRecord receipt, boolean duplicated) {
        if (ACTION_ACCEPTED_STATUS.equals(receipt.status())) {
            return new SyncActionOutcome(
                new AcceptedActionView(
                    receipt.offlineActionId(),
                    receipt.actionType(),
                    receipt.entityType(),
                    receipt.entityId(),
                    duplicated
                ),
                null
            );
        }
        return new SyncActionOutcome(
            null,
            new RejectedActionView(
                receipt.offlineActionId(),
                receipt.actionType(),
                receipt.entityType(),
                receipt.entityId(),
                receipt.errorCode(),
                receipt.errorMessage(),
                false,
                duplicated
            )
        );
    }

    private void applySupportedAction(UUID userId,
                                      String actionType,
                                      String entityType,
                                      UUID entityId,
                                      Map<String, Object> payload) {
        if (!SUPPORTED_ACTION_TYPE.equals(actionType)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_SYNC_ACTION_TYPE",
                "action_type is not supported for sync"
            );
        }
        if (!SUPPORTED_ENTITY_TYPE.equals(entityType)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SYNC_ENTITY_TYPE",
                "entity_type must be REVIEW_STATE for REVIEW_COMPLETE"
            );
        }
        if (entityId == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SYNC_ENTITY_ID",
                "entity_id must be a valid UUID"
            );
        }
        String completionStatus = normalizeRequired(
            asString(payload.get("completion_status")),
            "MISSING_COMPLETION_STATUS",
            "payload.completion_status is required"
        );
        reviewApplicationService.complete(
            entityId.toString(),
            new ReviewApplicationService.CompleteReviewCommand(
                userId.toString(),
                completionStatus,
                normalizeOptional(asString(payload.get("completion_reason"))),
                normalizeOptional(asString(payload.get("self_recall_result"))),
                normalizeOptional(asString(payload.get("note")))
            )
        );
    }

    private void appendSyncEvent(UUID userId,
                                 UUID traceId,
                                 String eventType,
                                 SyncActionReceiptRepository.SyncActionReceiptRecord receipt,
                                 boolean accepted,
                                 String errorCode,
                                 String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_id", receipt.clientId());
        payload.put("offline_action_id", receipt.offlineActionId());
        payload.put("action_type", receipt.actionType());
        payload.put("entity_type", receipt.entityType());
        payload.put("accepted", accepted);
        if (errorCode != null) {
            payload.put("error_code", errorCode);
        }
        if (errorMessage != null) {
            payload.put("error_message", errorMessage);
        }
        userActionEventRepository.append(
            userId,
            eventType,
            receipt.entityType(),
            receipt.entityId(),
            traceId,
            payload
        );
    }

    private List<SyncActionCommand> normalizeActions(List<SyncActionCommand> actions) {
        if (actions == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_SYNC_ACTIONS", "actions must be provided");
        }
        if (actions.size() > MAX_ACTIONS_PER_REQUEST) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SYNC_ACTIONS_TOO_MANY",
                "actions must not exceed " + MAX_ACTIONS_PER_REQUEST
            );
        }
        return actions;
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private UUID parseEntityId(String entityIdRaw) {
        String normalized = normalizeOptional(entityIdRaw);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SYNC_ENTITY_ID", "entity_id must be a valid UUID");
        }
    }

    private UUID parseEntityIdQuietly(String entityIdRaw) {
        try {
            return parseEntityId(entityIdRaw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Instant parseOccurredAt(String occurredAtRaw) {
        String normalized = normalizeOptional(occurredAtRaw);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_OCCURRED_AT", "occurred_at must be provided");
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OCCURRED_AT", "occurred_at must be a valid ISO-8601 timestamp");
        }
    }

    private Instant parseOccurredAtOrNull(String occurredAtRaw) {
        try {
            return parseOccurredAt(occurredAtRaw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String normalizeRequired(String value, String errorCode, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveErrorCode(RuntimeException exception) {
        if (exception instanceof ApiException apiException && apiException.errorCode() != null && !apiException.errorCode().isBlank()) {
            return apiException.errorCode();
        }
        return DEFAULT_SYNC_ERROR_CODE;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }

    private boolean isRetryable(ApiException exception) {
        if (exception.httpStatus() == null) {
            return true;
        }
        return !exception.httpStatus().is4xxClientError();
    }

    private void tryReleaseReservation(UUID userId, String clientId, String offlineActionId) {
        try {
            syncActionReceiptRepository.deleteByIdempotencyKey(userId, clientId, offlineActionId);
        } catch (RuntimeException exception) {
            log.warn(
                "module=SyncActionsApplicationService action=sync_action_reservation_release_failed user_id={} client_id={} offline_action_id={} error_code=SYNC_ACTION_RESERVATION_RELEASE_FAILED error_message={}",
                userId,
                clientId,
                offlineActionId,
                resolveErrorMessage(exception)
            );
        }
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public record SyncActionsCommand(
        String userId,
        String clientId,
        List<SyncActionCommand> actions
    ) {
    }

    public record SyncActionCommand(
        String offlineActionId,
        String actionType,
        String entityType,
        String entityId,
        Map<String, Object> payload,
        String occurredAt
    ) {
    }

    public record SyncActionsResult(
        List<AcceptedActionView> accepted,
        List<RejectedActionView> rejected,
        String traceId,
        Instant serverSyncCursor
    ) {
    }

    public record AcceptedActionView(
        String offlineActionId,
        String actionType,
        String entityType,
        UUID entityId,
        boolean duplicated
    ) {
    }

    public record RejectedActionView(
        String offlineActionId,
        String actionType,
        String entityType,
        UUID entityId,
        String errorCode,
        String errorMessage,
        boolean retryable,
        boolean duplicated
    ) {
    }

    private record SyncActionOutcome(
        AcceptedActionView accepted,
        RejectedActionView rejected
    ) {
    }
}
