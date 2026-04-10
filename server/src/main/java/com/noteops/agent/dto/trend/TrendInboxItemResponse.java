package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.repository.trend.TrendItemRepository;

import java.time.Instant;
import java.util.Map;

public record TrendInboxItemResponse(
    @JsonProperty("trend_item_id")
    String trendItemId,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_type")
    String sourceType,
    @JsonProperty("source_item_key")
    String sourceItemKey,
    String title,
    String url,
    String summary,
    @JsonProperty("normalized_score")
    double normalizedScore,
    @JsonProperty("status")
    String status,
    @JsonProperty("suggested_action")
    String suggestedAction,
    @JsonProperty("analysis_payload")
    Map<String, Object> analysisPayload,
    @JsonProperty("source_published_at")
    Instant sourcePublishedAt,
    @JsonProperty("last_ingested_at")
    Instant lastIngestedAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static TrendInboxItemResponse from(TrendItemRepository.TrendItemRecord record) {
        return new TrendInboxItemResponse(
            record.id().toString(),
            record.userId().toString(),
            record.sourceType().name(),
            record.sourceItemKey(),
            record.title(),
            record.url(),
            record.summary(),
            record.normalizedScore(),
            record.status().name(),
            record.suggestedAction() == null ? null : record.suggestedAction().name(),
            record.analysisPayload().toMap(),
            record.sourcePublishedAt(),
            record.lastIngestedAt(),
            record.updatedAt()
        );
    }
}
