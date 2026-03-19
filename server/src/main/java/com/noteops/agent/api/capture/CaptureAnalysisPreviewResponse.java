package com.noteops.agent.api.capture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.domain.capture.CaptureAnalysisResult;

import java.util.List;

public record CaptureAnalysisPreviewResponse(
    @JsonProperty("title_candidate")
    String titleCandidate,
    String summary,
    @JsonProperty("key_points")
    List<String> keyPoints,
    List<String> tags,
    @JsonProperty("idea_candidate")
    String ideaCandidate,
    Double confidence,
    String language,
    List<String> warnings
) {

    public static CaptureAnalysisPreviewResponse from(CaptureAnalysisResult value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new CaptureAnalysisPreviewResponse(
            value.titleCandidate(),
            value.summary(),
            value.keyPoints(),
            value.tags(),
            value.ideaCandidate(),
            value.confidence(),
            value.language(),
            value.warnings()
        );
    }
}
