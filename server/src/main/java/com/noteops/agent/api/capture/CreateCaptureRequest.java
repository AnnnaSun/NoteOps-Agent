package com.noteops.agent.api.capture;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCaptureRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("input_type")
    String inputType,
    @JsonProperty("raw_input")
    String rawInput,
    @JsonProperty("source_uri")
    String sourceUri
) {
}
