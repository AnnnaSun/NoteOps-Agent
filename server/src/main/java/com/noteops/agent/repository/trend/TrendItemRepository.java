package com.noteops.agent.repository.trend;

import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TrendItemRepository {

    TrendItemRecord create(UUID userId,
                           TrendSourceType sourceType,
                           String sourceItemKey,
                           String title,
                           String url,
                           String summary,
                           double normalizedScore,
                           TrendAnalysisPayload analysisPayload,
                           Map<String, Object> extraAttributes,
                           TrendItemStatus status,
                           TrendActionType suggestedAction,
                           Instant sourcePublishedAt,
                           Instant lastIngestedAt,
                           UUID convertedNoteId,
                           UUID convertedIdeaId);

    Optional<TrendItemRecord> findByIdAndUserId(UUID trendItemId, UUID userId);

    Optional<TrendItemRecord> findBySourceKey(UUID userId, TrendSourceType sourceType, String sourceItemKey);

    TrendItemIngestResult upsertIngested(UUID userId,
                                         TrendSourceType sourceType,
                                         String sourceItemKey,
                                         String title,
                                         String url,
                                         double normalizedScore,
                                         Map<String, Object> extraAttributes,
                                         Instant sourcePublishedAt,
                                         Instant lastIngestedAt);

    List<TrendItemRecord> findAllByUserId(UUID userId);

    TrendItemRecord updateStatus(UUID trendItemId,
                                 UUID userId,
                                 TrendItemStatus status,
                                 TrendActionType suggestedAction,
                                 UUID convertedNoteId,
                                 UUID convertedIdeaId);

    record TrendItemRecord(
        UUID id,
        UUID userId,
        TrendSourceType sourceType,
        String sourceItemKey,
        String title,
        String url,
        String summary,
        double normalizedScore,
        TrendAnalysisPayload analysisPayload,
        Map<String, Object> extraAttributes,
        TrendItemStatus status,
        TrendActionType suggestedAction,
        Instant sourcePublishedAt,
        Instant lastIngestedAt,
        UUID convertedNoteId,
        UUID convertedIdeaId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    record TrendItemIngestResult(
        TrendItemRecord trendItem,
        IngestAction action
    ) {
    }

    enum IngestAction {
        INSERTED,
        DEDUPED
    }
}
