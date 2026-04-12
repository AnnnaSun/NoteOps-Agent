package com.noteops.agent.model.trend;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisPayloadTest {

    @Test
    void ignoresUnknownSuggestedActionWhenLoadingFromMap() {
        TrendAnalysisPayload payload = TrendAnalysisPayload.fromMap(Map.of(
            "summary", "Summary",
            "why_it_matters", "Reason",
            "topic_tags", List.of("trend"),
            "signal_type", "DISCUSSION",
            "note_worthy", true,
            "idea_worthy", false,
            "suggested_action", "REALLY_UNKNOWN",
            "reasoning_summary", "Why"
        ));

        assertThat(payload.suggestedAction()).isNull();
        assertThat(payload.summary()).isEqualTo("Summary");
        assertThat(payload.reasoningSummary()).isEqualTo("Why");
    }
}
