package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendActionResponse(
    @JsonProperty("trend_item_id")
    String trendItemId,
    @JsonProperty("action_result")
    String actionResult,
    @JsonProperty("converted_note_id")
    String convertedNoteId,
    @JsonProperty("converted_idea_id")
    String convertedIdeaId
) {
}
