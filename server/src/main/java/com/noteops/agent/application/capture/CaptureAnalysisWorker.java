package com.noteops.agent.application.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.domain.capture.CaptureAnalysisResult;
import com.noteops.agent.domain.capture.CaptureAnalysisResultValidator;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CaptureAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(CaptureAnalysisWorker.class);

    private final CaptureAnalysisClient captureAnalysisClient;
    private final ObjectMapper objectMapper;

    public CaptureAnalysisWorker(CaptureAnalysisClient captureAnalysisClient, ObjectMapper objectMapper) {
        this.captureAnalysisClient = captureAnalysisClient;
        this.objectMapper = objectMapper;
    }

    public AnalysisOutcome analyze(CaptureAnalysisClient.AnalyzeRequest request) {
        CaptureAnalysisClient.AnalyzeResponse response = captureAnalysisClient.analyze(request);
        try {
            CaptureAnalysisResult result = objectMapper.readValue(response.rawJson(), CaptureAnalysisResult.class);
            CaptureAnalysisResult validated = CaptureAnalysisResultValidator.validate(result);
            log.info(
                "module=CaptureAnalysisWorker action=llm_output_validated result=SUCCESS trace_id={} user_id={} capture_source_type={} provider={} model={}",
                request.traceId(),
                request.userId(),
                request.sourceType(),
                response.provider(),
                response.model()
            );
            return new AnalysisOutcome(validated, response.provider().name(), response.model(), response.durationMs());
        } catch (Exception exception) {
            log.warn(
                "module=CaptureAnalysisWorker action=llm_output_validated result=FAILED trace_id={} user_id={} capture_source_type={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                request.sourceType(),
                CaptureFailureReason.LLM_OUTPUT_INVALID.name(),
                exception.getMessage()
            );
            throw new CapturePipelineException(
                CaptureFailureReason.LLM_OUTPUT_INVALID,
                "llm output is invalid: " + exception.getMessage(),
                exception
            );
        }
    }

    public record AnalysisOutcome(
        CaptureAnalysisResult analysisResult,
        String provider,
        String model,
        int durationMs
    ) {
    }
}
