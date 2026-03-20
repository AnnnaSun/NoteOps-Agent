package com.noteops.agent.service.capture;

import com.noteops.agent.model.capture.CaptureFailureReason;

public class CapturePipelineException extends RuntimeException {

    private final CaptureFailureReason failureReason;

    public CapturePipelineException(CaptureFailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public CapturePipelineException(CaptureFailureReason failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public CaptureFailureReason failureReason() {
        return failureReason;
    }
}
