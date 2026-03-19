package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureAiProvider;
import com.noteops.agent.domain.capture.CaptureInputType;

import java.util.UUID;

public interface CaptureAnalysisClient {

    AnalyzeResponse analyze(AnalyzeRequest request);

    record AnalyzeRequest(
        UUID userId,
        UUID traceId,
        CaptureInputType sourceType,
        String sourceUrl,
        String titleHint,
        String pageTitle,
        String cleanText
    ) {
    }

    record AnalyzeResponse(
        CaptureAiProvider provider,
        String model,
        String rawJson,
        int durationMs
    ) {
    }
}
