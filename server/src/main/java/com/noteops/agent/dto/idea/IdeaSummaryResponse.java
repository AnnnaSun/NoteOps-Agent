package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.idea.IdeaQueryService;

import java.time.Instant;

public record IdeaSummaryResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_mode")
    String sourceMode,
    @JsonProperty("source_note_id")
    String sourceNoteId,
    String title,
    String status,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static IdeaSummaryResponse from(IdeaQueryService.IdeaSummaryView view) {
        return new IdeaSummaryResponse(
            view.id().toString(),
            view.userId().toString(),
            view.sourceMode().name(),
            view.sourceNoteId() == null ? null : view.sourceNoteId().toString(),
            view.title(),
            view.status().name(),
            view.updatedAt()
        );
    }
}
