package com.noteops.agent.api.capture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.capture.CaptureApplicationService;

import java.time.Instant;

public record CaptureResponse(
    @JsonProperty("capture_job_id")
    String captureJobId,
    @JsonProperty("source_type")
    String sourceType,
    String status,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("failure_reason")
    String failureReason,
    @JsonProperty("analysis_preview")
    CaptureAnalysisPreviewResponse analysisPreview,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static CaptureResponse from(CaptureApplicationService.CaptureView view) {
        return new CaptureResponse(
            view.captureJobId().toString(),
            view.sourceType().name(),
            view.status().name(),
            view.noteId() == null ? null : view.noteId().toString(),
            view.failureReason() == null ? null : view.failureReason().name(),
            CaptureAnalysisPreviewResponse.from(view.analysisPreview()),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
