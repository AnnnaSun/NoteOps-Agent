package com.noteops.agent.service.sync;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.sync.SyncActionReceiptRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.review.ReviewApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncActionsApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-16T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void appliesReviewCompleteActionAndKeepsIdempotency() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID reviewId = UUID.randomUUID();
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        when(reviewApplicationService.complete(org.mockito.ArgumentMatchers.eq(reviewId.toString()), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ReviewApplicationService.ReviewCompletionView(
                reviewId,
                UUID.randomUUID(),
                com.noteops.agent.model.review.ReviewQueueType.RECALL,
                com.noteops.agent.model.review.ReviewCompletionStatus.COMPLETED,
                null,
                com.noteops.agent.model.review.ReviewSelfRecallResult.GOOD,
                null,
                Instant.parse("2026-04-18T00:00:00Z"),
                0,
                0,
                BigDecimal.TEN,
                null,
                null,
                List.of(),
                null
            ));

        InMemorySyncActionReceiptRepository receiptRepository = new InMemorySyncActionReceiptRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        SyncActionsApplicationService service = new SyncActionsApplicationService(
            receiptRepository,
            reviewApplicationService,
            traceRepository,
            eventRepository,
            CLOCK
        );

        SyncActionsApplicationService.SyncActionsCommand command = new SyncActionsApplicationService.SyncActionsCommand(
            userId.toString(),
            "web-client-1",
            List.of(
                new SyncActionsApplicationService.SyncActionCommand(
                    "offline-action-1",
                    "REVIEW_COMPLETE",
                    "REVIEW_STATE",
                    reviewId.toString(),
                    Map.of(
                        "completion_status", "COMPLETED",
                        "self_recall_result", "GOOD"
                    ),
                    "2026-04-16T08:58:00Z"
                )
            )
        );

        SyncActionsApplicationService.SyncActionsResult first = service.apply(command);
        SyncActionsApplicationService.SyncActionsResult second = service.apply(command);

        assertThat(first.accepted()).hasSize(1);
        assertThat(first.rejected()).isEmpty();
        assertThat(first.traceId()).isNotBlank();
        assertThat(first.serverSyncCursor()).isEqualTo(Instant.parse("2026-04-16T09:00:00Z"));
        assertThat(first.accepted().getFirst().duplicated()).isFalse();

        assertThat(second.accepted()).hasSize(1);
        assertThat(second.rejected()).isEmpty();
        assertThat(second.accepted().getFirst().duplicated()).isTrue();

        verify(reviewApplicationService, times(1))
            .complete(org.mockito.ArgumentMatchers.eq(reviewId.toString()), org.mockito.ArgumentMatchers.any());
        assertThat(receiptRepository.records).hasSize(1);
        assertThat(receiptRepository.records.values().iterator().next().status()).isEqualTo("ACCEPTED");
        assertThat(traceRepository.completedCount).isEqualTo(2);
        assertThat(eventRepository.events).extracting(SyncUserActionEventRecord::eventType)
            .contains("OFFLINE_ACTION_SYNC_ACCEPTED");

        var commandCaptor = forClass(ReviewApplicationService.CompleteReviewCommand.class);
        verify(reviewApplicationService).complete(org.mockito.ArgumentMatchers.eq(reviewId.toString()), commandCaptor.capture());
        assertThat(commandCaptor.getValue().completionStatus()).isEqualTo("COMPLETED");
        assertThat(commandCaptor.getValue().selfRecallResult()).isEqualTo("GOOD");
    }

    @Test
    void rejectsUnsupportedActionAndPersistsRejectedReceipt() {
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        InMemorySyncActionReceiptRepository receiptRepository = new InMemorySyncActionReceiptRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingUserActionEventRepository eventRepository = new RecordingUserActionEventRepository();
        SyncActionsApplicationService service = new SyncActionsApplicationService(
            receiptRepository,
            reviewApplicationService,
            traceRepository,
            eventRepository,
            CLOCK
        );

        SyncActionsApplicationService.SyncActionsResult result = service.apply(
            new SyncActionsApplicationService.SyncActionsCommand(
                "11111111-1111-1111-1111-111111111111",
                "web-client-1",
                List.of(
                    new SyncActionsApplicationService.SyncActionCommand(
                        "offline-action-1",
                        "UNKNOWN_ACTION",
                        "REVIEW_STATE",
                        UUID.randomUUID().toString(),
                        Map.of("completion_status", "COMPLETED"),
                        "2026-04-16T08:58:00Z"
                    )
                )
            )
        );

        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().getFirst().errorCode()).isEqualTo("UNSUPPORTED_SYNC_ACTION_TYPE");
        assertThat(result.rejected().getFirst().retryable()).isFalse();
        assertThat(receiptRepository.records.values().iterator().next().status()).isEqualTo("REJECTED");
        assertThat(eventRepository.events).extracting(SyncUserActionEventRecord::eventType)
            .contains("OFFLINE_ACTION_SYNC_REJECTED");
        verifyNoInteractions(reviewApplicationService);
    }

    @Test
    void returnsRetryableRejectedWhenReceiptIsAlreadyProcessing() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID reviewId = UUID.randomUUID();
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        InMemorySyncActionReceiptRepository receiptRepository = new InMemorySyncActionReceiptRepository();
        receiptRepository.records.put(
            receiptRepository.key(userId, "web-client-1", "offline-action-1"),
            new SyncActionReceiptRepository.SyncActionReceiptRecord(
                UUID.randomUUID(),
                userId,
                "web-client-1",
                "offline-action-1",
                "REVIEW_COMPLETE",
                "REVIEW_STATE",
                reviewId,
                UUID.randomUUID(),
                "PROCESSING",
                null,
                null,
                Map.of(),
                Instant.parse("2026-04-16T08:58:00Z"),
                Instant.parse("2026-04-16T09:00:00Z")
            )
        );

        SyncActionsApplicationService service = new SyncActionsApplicationService(
            receiptRepository,
            reviewApplicationService,
            new RecordingAgentTraceRepository(),
            new RecordingUserActionEventRepository(),
            CLOCK
        );

        SyncActionsApplicationService.SyncActionsResult result = service.apply(
            new SyncActionsApplicationService.SyncActionsCommand(
                userId.toString(),
                "web-client-1",
                List.of(
                    new SyncActionsApplicationService.SyncActionCommand(
                        "offline-action-1",
                        "REVIEW_COMPLETE",
                        "REVIEW_STATE",
                        reviewId.toString(),
                        Map.of("completion_status", "COMPLETED"),
                        "2026-04-16T08:58:00Z"
                    )
                )
            )
        );

        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().getFirst().errorCode()).isEqualTo("SYNC_ACTION_IN_PROGRESS");
        assertThat(result.rejected().getFirst().retryable()).isTrue();
        assertThat(result.rejected().getFirst().duplicated()).isTrue();
        verifyNoInteractions(reviewApplicationService);
    }

    @Test
    void throwsWhenReviewCompleteReturnsServerErrorAndKeepsActionRetryable() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID reviewId = UUID.randomUUID();
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        when(reviewApplicationService.complete(org.mockito.ArgumentMatchers.eq(reviewId.toString()), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "REVIEW_TEMPORARY_FAILURE", "temporary failure"));

        InMemorySyncActionReceiptRepository receiptRepository = new InMemorySyncActionReceiptRepository();
        SyncActionsApplicationService service = new SyncActionsApplicationService(
            receiptRepository,
            reviewApplicationService,
            new RecordingAgentTraceRepository(),
            new RecordingUserActionEventRepository(),
            CLOCK
        );

        assertThatThrownBy(() -> service.apply(
            new SyncActionsApplicationService.SyncActionsCommand(
                userId.toString(),
                "web-client-1",
                List.of(
                    new SyncActionsApplicationService.SyncActionCommand(
                        "offline-action-1",
                        "REVIEW_COMPLETE",
                        "REVIEW_STATE",
                        reviewId.toString(),
                        Map.of("completion_status", "COMPLETED"),
                        "2026-04-16T08:58:00Z"
                    )
                )
            )
        ))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo("REVIEW_TEMPORARY_FAILURE");

        assertThat(receiptRepository.records).isEmpty();
    }

    @Test
    void rejectsInvalidUserIdBeforeTraceCreation() {
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        SyncActionsApplicationService service = new SyncActionsApplicationService(
            new InMemorySyncActionReceiptRepository(),
            reviewApplicationService,
            new RecordingAgentTraceRepository(),
            new RecordingUserActionEventRepository(),
            CLOCK
        );

        assertThatThrownBy(() -> service.apply(
            new SyncActionsApplicationService.SyncActionsCommand(
                "bad-user-id",
                "web-client-1",
                List.of()
            )
        ))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo("INVALID_USER_ID");
    }

    private static final class InMemorySyncActionReceiptRepository implements SyncActionReceiptRepository {
        private final Map<String, SyncActionReceiptRecord> records = new HashMap<>();

        @Override
        public Optional<SyncActionReceiptRecord> findByIdempotencyKey(UUID userId, String clientId, String offlineActionId) {
            return Optional.ofNullable(records.get(key(userId, clientId, offlineActionId)));
        }

        @Override
        public SyncActionReservation reserveProcessing(SyncActionReceiptReserveCommand command) {
            String key = key(command.userId(), command.clientId(), command.offlineActionId());
            SyncActionReceiptRecord existing = records.get(key);
            if (existing != null) {
                return new SyncActionReservation(existing, false);
            }
            SyncActionReceiptRecord created = new SyncActionReceiptRecord(
                UUID.randomUUID(),
                command.userId(),
                command.clientId(),
                command.offlineActionId(),
                command.actionType(),
                command.entityType(),
                command.entityId(),
                command.traceId(),
                "PROCESSING",
                null,
                null,
                command.payload() == null ? Map.of() : command.payload(),
                command.occurredAt(),
                Instant.parse("2026-04-16T09:00:00Z")
            );
            records.put(key, created);
            return new SyncActionReservation(created, true);
        }

        @Override
        public SyncActionReceiptRecord updateOutcome(SyncActionReceiptOutcomeCommand command) {
            String key = key(command.userId(), command.clientId(), command.offlineActionId());
            SyncActionReceiptRecord existing = records.get(key);
            if (existing == null) {
                throw new IllegalStateException("missing receipt");
            }
            SyncActionReceiptRecord updated = new SyncActionReceiptRecord(
                existing.id(),
                existing.userId(),
                existing.clientId(),
                existing.offlineActionId(),
                existing.actionType(),
                existing.entityType(),
                existing.entityId(),
                command.traceId(),
                command.status(),
                command.errorCode(),
                command.errorMessage(),
                existing.payload(),
                existing.occurredAt(),
                Instant.parse("2026-04-16T09:00:00Z")
            );
            records.put(key, updated);
            return updated;
        }

        @Override
        public void deleteByIdempotencyKey(UUID userId, String clientId, String offlineActionId) {
            String key = key(userId, clientId, offlineActionId);
            SyncActionReceiptRecord existing = records.get(key);
            if (existing != null && "PROCESSING".equals(existing.status())) {
                records.remove(key);
            }
        }

        private String key(UUID userId, String clientId, String offlineActionId) {
            return userId + "|" + clientId + "|" + offlineActionId;
        }
    }

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {
        private UUID lastTraceId;
        private int completedCount;

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            lastTraceId = UUID.randomUUID();
            return lastTraceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completedCount++;
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {
        private final List<SyncUserActionEventRecord> events = new java.util.ArrayList<>();

        @Override
        public void append(UUID userId,
                           String eventType,
                           String entityType,
                           UUID entityId,
                           UUID traceId,
                           Map<String, Object> payload) {
            events.add(new SyncUserActionEventRecord(userId, eventType, entityType, entityId, traceId, payload));
        }
    }

    private record SyncUserActionEventRecord(
        UUID userId,
        String eventType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload
    ) {
    }
}
