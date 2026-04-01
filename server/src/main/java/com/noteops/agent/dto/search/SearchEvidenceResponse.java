package com.noteops.agent.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.search.SearchGovernanceApplicationService;

public record SearchEvidenceResponse(
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("content_id")
    String contentId,
    @JsonProperty("content_type")
    String contentType,
    @JsonProperty("source_uri")
    String sourceUri,
    @JsonProperty("relation_label")
    String relationLabel
) {

    public static SearchEvidenceResponse from(SearchGovernanceApplicationService.SearchEvidenceSaveView view) {
        return new SearchEvidenceResponse(
            view.noteId().toString(),
            view.contentId().toString(),
            view.contentType(),
            view.sourceUri(),
            view.relationLabel()
        );
    }
}
