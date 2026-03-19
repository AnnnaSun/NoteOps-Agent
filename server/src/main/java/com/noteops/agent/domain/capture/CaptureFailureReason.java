package com.noteops.agent.domain.capture;

public enum CaptureFailureReason {
    EXTRACTION_FAILED,
    LLM_CALL_FAILED,
    LLM_OUTPUT_INVALID,
    CONSOLIDATION_FAILED
}
