package com.noteops.agent.dto.capture;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCaptureRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("source_type")
    @JsonAlias("input_type")
    String sourceType,
    @JsonProperty("raw_text")
    @JsonAlias("raw_input")
    String rawText,
    @JsonProperty("source_url")
    @JsonAlias("source_uri")
    String sourceUrl,
    @JsonProperty("title_hint")
    String titleHint
) {
}
