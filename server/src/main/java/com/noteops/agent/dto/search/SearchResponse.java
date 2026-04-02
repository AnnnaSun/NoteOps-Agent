package com.noteops.agent.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.search.SearchApplicationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchResponse(
    String query,
    @JsonProperty("exact_matches")
    List<ExactMatchResponse> exactMatches,
    @JsonProperty("related_matches")
    List<RelatedMatchResponse> relatedMatches,
    @JsonProperty("external_supplements")
    List<ExternalSupplementResponse> externalSupplements,
    @JsonProperty("ai_enhancement_status")
    String aiEnhancementStatus
) {

    public static SearchResponse from(SearchApplicationService.SearchView view) {
        return new SearchResponse(
            view.query(),
            view.exactMatches().stream().map(ExactMatchResponse::from).toList(),
            view.relatedMatches().stream().map(RelatedMatchResponse::from).toList(),
            view.externalSupplements().stream().map(ExternalSupplementResponse::from).toList(),
            view.aiEnhancementStatus()
        );
    }

    public record ExactMatchResponse(
        @JsonProperty("note_id")
        UUID noteId,
        String title,
        @JsonProperty("current_summary")
        String currentSummary,
        @JsonProperty("current_key_points")
        List<String> currentKeyPoints,
        @JsonProperty("latest_content")
        String latestContent,
        @JsonProperty("updated_at")
        Instant updatedAt
    ) {

        public static ExactMatchResponse from(SearchApplicationService.SearchExactMatchView view) {
            return new ExactMatchResponse(
                view.noteId(),
                view.title(),
                view.currentSummary(),
                view.currentKeyPoints(),
                view.latestContent(),
                view.updatedAt()
            );
        }
    }

    public record RelatedMatchResponse(
        @JsonProperty("note_id")
        UUID noteId,
        String title,
        @JsonProperty("current_summary")
        String currentSummary,
        @JsonProperty("current_key_points")
        List<String> currentKeyPoints,
        @JsonProperty("latest_content")
        String latestContent,
        @JsonProperty("relation_reason")
        String relationReason,
        @JsonProperty("updated_at")
        Instant updatedAt,
        @JsonProperty("is_ai_enhanced")
        boolean aiEnhanced
    ) {

        public static RelatedMatchResponse from(SearchApplicationService.SearchRelatedMatchView view) {
            return new RelatedMatchResponse(
                view.noteId(),
                view.title(),
                view.currentSummary(),
                view.currentKeyPoints(),
                view.latestContent(),
                view.relationReason(),
                view.updatedAt(),
                view.aiEnhanced()
            );
        }
    }

    public record ExternalSupplementResponse(
        @JsonProperty("source_name")
        String sourceName,
        @JsonProperty("source_uri")
        String sourceUri,
        String summary,
        List<String> keywords,
        @JsonProperty("relation_label")
        String relationLabel,
        @JsonProperty("relation_tags")
        List<String> relationTags,
        @JsonProperty("summary_snippet")
        String summarySnippet,
        @JsonProperty("is_ai_enhanced")
        boolean aiEnhanced
    ) {

        public static ExternalSupplementResponse from(SearchApplicationService.ExternalSupplementView view) {
            return new ExternalSupplementResponse(
                view.sourceName(),
                view.sourceUri(),
                view.summary(),
                view.keywords(),
                view.relationLabel(),
                view.relationTags(),
                view.summarySnippet(),
                view.aiEnhanced()
            );
        }
    }
}
