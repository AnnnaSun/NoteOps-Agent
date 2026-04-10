package com.noteops.agent.service.trend;

import com.noteops.agent.model.trend.TrendSourceType;

import java.time.Instant;
import java.util.Map;

public record FetchedTrendCandidate(
    TrendSourceType sourceType,
    String sourceItemKey,
    String title,
    String url,
    double normalizedScore,
    Instant sourcePublishedAt,
    Map<String, Object> extraAttributes
) {
}
