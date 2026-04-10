package com.noteops.agent.service.trend;

import com.noteops.agent.model.trend.TrendSourceType;

import java.util.List;
import java.util.UUID;

public interface TrendSourceConnector {

    TrendSourceType sourceType();

    String displayName();

    List<FetchedTrendCandidate> fetchCandidates(FetchCommand command);

    record FetchCommand(
        UUID userId,
        UUID traceId,
        int fetchLimit,
        List<String> keywordBias
    ) {
    }
}
