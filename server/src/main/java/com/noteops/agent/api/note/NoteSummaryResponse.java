package com.noteops.agent.api.note;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.note.NoteQueryService;

import java.time.Instant;
import java.util.List;

public record NoteSummaryResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    String title,
    @JsonProperty("current_summary")
    String currentSummary,
    @JsonProperty("current_key_points")
    List<String> currentKeyPoints,
    @JsonProperty("latest_content_id")
    String latestContentId,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static NoteSummaryResponse from(NoteQueryService.NoteSummaryView view) {
        return new NoteSummaryResponse(
            view.id().toString(),
            view.userId().toString(),
            view.title(),
            view.currentSummary(),
            view.currentKeyPoints(),
            view.latestContentId() == null ? null : view.latestContentId().toString(),
            view.updatedAt()
        );
    }
}
