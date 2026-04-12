package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.repository.trend.TrendItemRepository;
import com.noteops.agent.service.trend.TrendActionApplicationService;

public record TrendActionResponse(
    @JsonProperty("trace_id")
    String traceId,
    @JsonProperty("trend_item_id")
    String trendItemId,
    @JsonProperty("action_result")
    String actionResult,
    @JsonProperty("converted_note_id")
    String convertedNoteId,
    @JsonProperty("converted_idea_id")
    String convertedIdeaId
) {

    public static TrendActionResponse from(TrendActionApplicationService.ActionResult result) {
        TrendItemRepository.TrendItemRecord trendItem = result.trendItem();
        return new TrendActionResponse(
            result.traceId(),
            trendItem.id().toString(),
            trendItem.status().name(),
            trendItem.convertedNoteId() == null ? null : trendItem.convertedNoteId().toString(),
            trendItem.convertedIdeaId() == null ? null : trendItem.convertedIdeaId().toString()
        );
    }
}
