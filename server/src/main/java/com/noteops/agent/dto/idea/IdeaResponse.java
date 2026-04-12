package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.repository.idea.IdeaRepository;

import java.time.Instant;

public record IdeaResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_mode")
    String sourceMode,
    @JsonProperty("source_note_id")
    String sourceNoteId,
    @JsonProperty("source_trend_item_id")
    String sourceTrendItemId,
    String title,
    @JsonProperty("raw_description")
    String rawDescription,
    String status,
    @JsonProperty("assessment_result")
    IdeaAssessmentResult assessmentResult,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static IdeaResponse from(IdeaRepository.IdeaRecord record) {
        return new IdeaResponse(
            record.id().toString(),
            record.userId().toString(),
            record.sourceMode().name(),
            record.sourceNoteId() == null ? null : record.sourceNoteId().toString(),
            record.sourceTrendItemId() == null ? null : record.sourceTrendItemId().toString(),
            record.title(),
            record.rawDescription(),
            record.status().name(),
            record.assessmentResult(),
            record.createdAt(),
            record.updatedAt()
        );
    }
}
