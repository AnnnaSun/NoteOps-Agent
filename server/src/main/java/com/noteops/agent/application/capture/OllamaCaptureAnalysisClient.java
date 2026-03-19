package com.noteops.agent.application.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.domain.capture.CaptureAiProvider;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OllamaCaptureAnalysisClient implements CaptureProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaCaptureAnalysisClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CaptureAiProperties properties;
    private final ToolInvocationLogRepository toolInvocationLogRepository;

    @Autowired
    public OllamaCaptureAnalysisClient(ObjectMapper objectMapper,
                                       CaptureAiProperties properties,
                                       ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    OllamaCaptureAnalysisClient(HttpClient httpClient,
                                ObjectMapper objectMapper,
                                CaptureAiProperties properties,
                                ToolInvocationLogRepository toolInvocationLogRepository) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
    }

    @Override
    public CaptureAiProvider provider() {
        return CaptureAiProvider.OLLAMA;
    }

    @Override
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String model = properties.ollama().model();
        if (model == null) {
            throw llmFailure(model, "ollama provider is missing model", null);
        }

        long startedAt = System.nanoTime();
        log.info(
            "module=OllamaCaptureAnalysisClient action=llm_call_start result=RUNNING trace_id={} user_id={} provider={} model={} source_type={}",
            request.traceId(),
            request.userId(),
            CaptureAiProvider.OLLAMA,
            model,
            request.sourceType()
        );
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", CaptureAnalysisJsonSchema.systemPrompt()),
                Map.of("role", "user", "content", CaptureAnalysisJsonSchema.userPrompt(request))
            ));
            requestBody.put("format", CaptureAnalysisJsonSchema.schema());

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.ollama().baseUrl() + "/api/chat"))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int durationMs = durationMs(startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                appendFailureLog(request, model, durationMs, "OLLAMA_HTTP_" + response.statusCode(), response.body());
                throw llmFailure(model, "ollama call failed with status " + response.statusCode(), null);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode messageContent = responseJson.path("message").path("content");
            String content = messageContent.asText(null);
            if (content == null || content.isBlank()) {
                appendFailureLog(request, model, durationMs, "OLLAMA_EMPTY_RESPONSE", "ollama response content is empty");
                throw llmFailure(model, "ollama response content is empty", null);
            }

            appendSuccessLog(request, model, durationMs, content);
            log.info(
                "module=OllamaCaptureAnalysisClient action=llm_call_end result=SUCCESS trace_id={} user_id={} provider={} model={} source_type={} duration_ms={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.OLLAMA,
                model,
                request.sourceType(),
                durationMs
            );
            return new AnalyzeResponse(CaptureAiProvider.OLLAMA, model, content, durationMs);
        } catch (CapturePipelineException exception) {
            log.warn(
                "module=OllamaCaptureAnalysisClient action=llm_call_end result=FAILED trace_id={} user_id={} provider={} model={} source_type={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.OLLAMA,
                model,
                request.sourceType(),
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            int durationMs = durationMs(startedAt);
            appendFailureLog(request, model, durationMs, "OLLAMA_CALL_ERROR", exception.getMessage());
            log.warn(
                "module=OllamaCaptureAnalysisClient action=llm_call_end result=FAILED trace_id={} user_id={} provider={} model={} source_type={} duration_ms={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.OLLAMA,
                model,
                request.sourceType(),
                durationMs,
                CaptureFailureReason.LLM_CALL_FAILED.name(),
                exception.getMessage()
            );
            throw llmFailure(model, "ollama call failed: " + exception.getMessage(), exception);
        }
    }

    private void appendSuccessLog(AnalyzeRequest request, String model, int durationMs, String content) {
        toolInvocationLogRepository.append(
            request.userId(),
            request.traceId(),
            "capture.analysis.ollama",
            "SUCCESS",
            inputDigest(model, request),
            Map.of(
                "result", "SUCCESS",
                "response_chars", content.length()
            ),
            durationMs,
            null,
            null
        );
    }

    private void appendFailureLog(AnalyzeRequest request, String model, int durationMs, String errorCode, String errorMessage) {
        toolInvocationLogRepository.append(
            request.userId(),
            request.traceId(),
            "capture.analysis.ollama",
            "FAILED",
            inputDigest(model, request),
            Map.of("result", "FAILED"),
            durationMs,
            errorCode,
            errorMessage
        );
    }

    private Map<String, Object> inputDigest(String model, AnalyzeRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", CaptureAiProvider.OLLAMA.name());
        payload.put("request_type", "CAPTURE_ANALYSIS");
        payload.put("source_type", request.sourceType().name());
        if (model != null) {
            payload.put("model", model);
        }
        return payload;
    }

    private CapturePipelineException llmFailure(String model, String message, Exception cause) {
        String fullMessage = "provider=OLLAMA model=" + model + " " + message;
        if (cause == null) {
            return new CapturePipelineException(CaptureFailureReason.LLM_CALL_FAILED, fullMessage);
        }
        return new CapturePipelineException(CaptureFailureReason.LLM_CALL_FAILED, fullMessage, cause);
    }

    private int durationMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }
}
