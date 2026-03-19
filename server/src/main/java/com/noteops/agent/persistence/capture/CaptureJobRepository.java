package com.noteops.agent.persistence.capture;

import com.noteops.agent.application.capture.CaptureApplicationService;
import com.noteops.agent.domain.capture.CaptureAnalysisResult;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.domain.capture.CaptureInputType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CaptureJobRepository {

    void createReceived(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput);

    void markExtracting(UUID captureId);

    void saveExtractionResult(UUID captureId, Map<String, Object> extractedPayload);

    void markAnalyzing(UUID captureId);

    void saveAnalysisResult(UUID captureId, CaptureAnalysisResult analysisResult);

    void markConsolidating(UUID captureId);

    void saveConsolidationResult(UUID captureId, Map<String, Object> consolidationResult);

    void markCompleted(UUID captureId);

    void markFailed(UUID captureId, CaptureFailureReason failureReason, String errorMessage);

    Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId);
}
