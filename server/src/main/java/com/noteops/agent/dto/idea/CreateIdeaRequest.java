package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateIdeaRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_mode")
    String sourceMode,
    @JsonProperty("source_note_id")
    String sourceNoteId,
    String title,
    @JsonProperty("raw_description")
    String rawDescription
) {
}
