package com.noteops.agent.service.search;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface SearchAiEnhancer {

    SearchAiEnhancementResult enhance(SearchAiEnhancementRequest request);

    record SearchAiEnhancementRequest(
        UUID userId,
        UUID traceId,
        String query,
        List<RelatedCandidate> relatedCandidates,
        List<ExternalCandidate> externalCandidates
    ) {
    }

    record RelatedCandidate(
        UUID noteId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        List<String> currentTags,
        String sourceUri,
        String latestContentType,
        String fallbackReason
    ) {
    }

    record ExternalCandidate(
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        List<String> relationTags,
        String fallbackRelationLabel,
        String fallbackSummarySnippet
    ) {
    }

    record RelatedEnhancement(
        String relationReason
    ) {
    }

    record ExternalEnhancement(
        String relationLabel,
        List<String> keywords,
        String summarySnippet
    ) {
    }

    record SearchAiEnhancementResult(
        Map<UUID, RelatedEnhancement> relatedByNoteId,
        Map<String, ExternalEnhancement> externalBySourceUri
    ) {
        public SearchAiEnhancementResult {
            relatedByNoteId = relatedByNoteId == null ? Map.of() : Map.copyOf(relatedByNoteId);
            externalBySourceUri = externalBySourceUri == null ? Map.of() : Map.copyOf(externalBySourceUri);
        }
    }
}
