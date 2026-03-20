package com.noteops.agent.service.capture;

import com.noteops.agent.service.ai.AiProvider;
import com.noteops.agent.model.capture.CaptureInputType;

import java.util.UUID;

public interface CaptureAnalysisClient {

    String ROUTE_KEY = "capture-analysis";
    String REQUEST_TYPE = "CAPTURE_ANALYSIS";
    String TOOL_NAME = "capture.analysis";

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
        AiProvider provider,
        String model,
        String rawJson,
        int durationMs
    ) {
    }
}
