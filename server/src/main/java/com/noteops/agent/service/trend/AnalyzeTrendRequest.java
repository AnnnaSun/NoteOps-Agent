package com.noteops.agent.service.trend;

import com.noteops.agent.model.trend.TrendSourceType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnalyzeTrendRequest(
    UUID userId,
    UUID traceId,
    UUID trendItemId,
    TrendSourceType sourceType,
    String sourceItemKey,
    String title,
    String url,
    double normalizedScore,
    Instant sourcePublishedAt,
    Map<String, Object> extraAttributes
) {
}
