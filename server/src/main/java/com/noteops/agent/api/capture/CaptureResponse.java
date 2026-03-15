package com.noteops.agent.api.capture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.capture.CaptureApplicationService;

import java.time.Instant;

public record CaptureResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("input_type")
    String inputType,
    @JsonProperty("source_uri")
    String sourceUri,
    @JsonProperty("raw_input")
    String rawInput,
    String status,
    @JsonProperty("error_code")
    String errorCode,
    @JsonProperty("error_message")
    String errorMessage,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt,
    @JsonProperty("note_id")
    String noteId
) {

    public static CaptureResponse from(CaptureApplicationService.CaptureView view) {
        return new CaptureResponse(
            view.id().toString(),
            view.userId().toString(),
            view.inputType().name(),
            view.sourceUri(),
            view.rawInput(),
            view.status().name(),
            view.errorCode(),
            view.errorMessage(),
            view.createdAt(),
            view.updatedAt(),
            view.noteId()
        );
    }
}
