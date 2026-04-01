package com.noteops.agent.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchSupplementActionRequest(
    @JsonProperty("user_id")
    String userId,
    String query,
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
    String summarySnippet
) {
}
