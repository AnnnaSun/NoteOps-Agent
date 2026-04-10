package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TrendCandidateNormalizer {

    public FetchedTrendCandidate normalize(FetchedTrendCandidate candidate) {
        String sourceItemKey = trimRequired(candidate.sourceItemKey(), "source_item_key");
        String title = trimRequired(candidate.title(), "title");
        String url = trimRequired(candidate.url(), "url");
        double normalizedScore = roundToTwoDecimals(clampScore(candidate.normalizedScore()));

        Map<String, Object> extraAttributes = new LinkedHashMap<>();
        if (candidate.extraAttributes() != null) {
            extraAttributes.putAll(candidate.extraAttributes());
        }

        return new FetchedTrendCandidate(
            candidate.sourceType(),
            sourceItemKey,
            title,
            url,
            normalizedScore,
            candidate.sourcePublishedAt(),
            Map.copyOf(extraAttributes)
        );
    }

    private String trimRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_SOURCE_RESPONSE_INVALID",
                "trend candidate field " + fieldName + " must not be blank"
            );
        }
        return value.trim();
    }

    private double clampScore(double score) {
        if (score < 0) {
            return 0;
        }
        if (score > 100) {
            return 100;
        }
        return score;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
