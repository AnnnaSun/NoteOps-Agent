package com.noteops.agent.repository.sync;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SyncActionReceiptRepository {

    Optional<SyncActionReceiptRecord> findByIdempotencyKey(UUID userId, String clientId, String offlineActionId);

    SyncActionReservation reserveProcessing(SyncActionReceiptReserveCommand command);

    SyncActionReceiptRecord updateOutcome(SyncActionReceiptOutcomeCommand command);

    void deleteByIdempotencyKey(UUID userId, String clientId, String offlineActionId);

    record SyncActionReceiptReserveCommand(
        UUID userId,
        String clientId,
        String offlineActionId,
        String actionType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload,
        Instant occurredAt
    ) {
    }

    record SyncActionReceiptOutcomeCommand(
        UUID userId,
        String clientId,
        String offlineActionId,
        UUID traceId,
        String status,
        String errorCode,
        String errorMessage
    ) {
    }

    record SyncActionReservation(
        SyncActionReceiptRecord record,
        boolean reserved
    ) {
    }

    record SyncActionReceiptRecord(
        UUID id,
        UUID userId,
        String clientId,
        String offlineActionId,
        String actionType,
        String entityType,
        UUID entityId,
        UUID traceId,
        String status,
        String errorCode,
        String errorMessage,
        Map<String, Object> payload,
        Instant occurredAt,
        Instant processedAt
    ) {
    }
}
