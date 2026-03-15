package com.noteops.agent.persistence.capture;

import com.noteops.agent.application.capture.CaptureApplicationService;
import com.noteops.agent.domain.capture.CaptureInputType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CaptureJobRepository {

    void createReceived(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput);

    void updateExtraction(UUID captureId, Map<String, Object> extractedPayload);

    void updateAnalysis(UUID captureId, Map<String, Object> analysisResult);

    void updateConsolidation(UUID captureId, Map<String, Object> consolidationResult);

    void markCompleted(UUID captureId);

    void markFailed(UUID captureId, String errorCode, String errorMessage);

    Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId);
}
