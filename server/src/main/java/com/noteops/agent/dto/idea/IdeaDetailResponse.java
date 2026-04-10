package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.service.idea.IdeaQueryService;

import java.time.Instant;

public record IdeaDetailResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_mode")
    String sourceMode,
    @JsonProperty("source_note_id")
    String sourceNoteId,
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

    public static IdeaDetailResponse from(IdeaQueryService.IdeaDetailView view) {
        return new IdeaDetailResponse(
            view.id().toString(),
            view.userId().toString(),
            view.sourceMode().name(),
            view.sourceNoteId() == null ? null : view.sourceNoteId().toString(),
            view.title(),
            view.rawDescription(),
            view.status().name(),
            view.assessmentResult(),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
