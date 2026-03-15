package com.noteops.agent.api.note;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.note.NoteQueryService;

import java.time.Instant;
import java.util.List;

public record NoteDetailResponse(
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
    @JsonProperty("latest_content_type")
    String latestContentType,
    @JsonProperty("source_uri")
    String sourceUri,
    @JsonProperty("raw_text")
    String rawText,
    @JsonProperty("clean_text")
    String cleanText,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static NoteDetailResponse from(NoteQueryService.NoteDetailView view) {
        return new NoteDetailResponse(
            view.id().toString(),
            view.userId().toString(),
            view.title(),
            view.currentSummary(),
            view.currentKeyPoints(),
            view.latestContentId() == null ? null : view.latestContentId().toString(),
            view.latestContentType(),
            view.sourceUri(),
            view.rawText(),
            view.cleanText(),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
