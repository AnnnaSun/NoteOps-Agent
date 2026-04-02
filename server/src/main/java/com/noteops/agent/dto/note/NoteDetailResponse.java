package com.noteops.agent.dto.note;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.note.NoteQueryService;

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
    Instant updatedAt,
    @JsonProperty("evidence_blocks")
    List<NoteEvidenceBlockResponse> evidenceBlocks
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
            view.updatedAt(),
            view.evidenceBlocks().stream()
                .map(NoteEvidenceBlockResponse::from)
                .toList()
        );
    }

    public record NoteEvidenceBlockResponse(
        String id,
        @JsonProperty("content_type")
        String contentType,
        @JsonProperty("source_uri")
        String sourceUri,
        @JsonProperty("source_name")
        String sourceName,
        @JsonProperty("relation_label")
        String relationLabel,
        @JsonProperty("summary_snippet")
        String summarySnippet,
        @JsonProperty("created_at")
        Instant createdAt
    ) {

        static NoteEvidenceBlockResponse from(NoteQueryService.NoteEvidenceView view) {
            return new NoteEvidenceBlockResponse(
                view.id().toString(),
                view.contentType(),
                view.sourceUri(),
                view.sourceName(),
                view.relationLabel(),
                view.summarySnippet(),
                view.createdAt()
            );
        }
    }
}
