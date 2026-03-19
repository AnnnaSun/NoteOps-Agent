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
public class DeepSeekCaptureAnalysisClient implements CaptureProviderClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekCaptureAnalysisClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CaptureAiProperties properties;
    private final ToolInvocationLogRepository toolInvocationLogRepository;

    @Autowired
    public DeepSeekCaptureAnalysisClient(ObjectMapper objectMapper,
                                         CaptureAiProperties properties,
                                         ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    DeepSeekCaptureAnalysisClient(HttpClient httpClient,
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
        return CaptureAiProvider.DEEPSEEK;
    }

    @Override
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String model = properties.deepseek().model();
        String apiKey = properties.deepseek().apiKey();
        if (model == null || apiKey == null) {
            throw llmFailure(request, null, "deepseek provider is missing api key or model", null);
        }

        long startedAt = System.nanoTime();
        log.info(
            "module=DeepSeekCaptureAnalysisClient action=llm_call_start result=RUNNING trace_id={} user_id={} provider={} model={} source_type={}",
            request.traceId(),
            request.userId(),
            CaptureAiProvider.DEEPSEEK,
            model,
            request.sourceType()
        );
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", CaptureAnalysisJsonSchema.systemPrompt()),
                Map.of("role", "user", "content", CaptureAnalysisJsonSchema.userPrompt(request))
            ));
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.deepseek().baseUrl() + "/chat/completions"))
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int durationMs = durationMs(startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                appendFailureLog(request, model, durationMs, "DEEPSEEK_HTTP_" + response.statusCode(), response.body());
                throw llmFailure(request, model, "deepseek call failed with status " + response.statusCode(), null);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode contentNode = responseJson.path("choices").path(0).path("message").path("content");
            String content = contentNode.asText(null);
            if (content == null || content.isBlank()) {
                appendFailureLog(request, model, durationMs, "DEEPSEEK_EMPTY_RESPONSE", "deepseek response content is empty");
                throw llmFailure(request, model, "deepseek response content is empty", null);
            }

            appendSuccessLog(request, model, durationMs, content);
            log.info(
                "module=DeepSeekCaptureAnalysisClient action=llm_call_end result=SUCCESS trace_id={} user_id={} provider={} model={} source_type={} duration_ms={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.DEEPSEEK,
                model,
                request.sourceType(),
                durationMs
            );
            return new AnalyzeResponse(CaptureAiProvider.DEEPSEEK, model, content, durationMs);
        } catch (CapturePipelineException exception) {
            log.warn(
                "module=DeepSeekCaptureAnalysisClient action=llm_call_end result=FAILED trace_id={} user_id={} provider={} model={} source_type={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.DEEPSEEK,
                model,
                request.sourceType(),
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            int durationMs = durationMs(startedAt);
            appendFailureLog(request, model, durationMs, "DEEPSEEK_CALL_ERROR", exception.getMessage());
            log.warn(
                "module=DeepSeekCaptureAnalysisClient action=llm_call_end result=FAILED trace_id={} user_id={} provider={} model={} source_type={} duration_ms={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                CaptureAiProvider.DEEPSEEK,
                model,
                request.sourceType(),
                durationMs,
                CaptureFailureReason.LLM_CALL_FAILED.name(),
                exception.getMessage()
            );
            throw llmFailure(request, model, "deepseek call failed: " + exception.getMessage(), exception);
        }
    }

    private void appendSuccessLog(AnalyzeRequest request, String model, int durationMs, String content) {
        toolInvocationLogRepository.append(
            request.userId(),
            request.traceId(),
            "capture.analysis.deepseek",
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
            "capture.analysis.deepseek",
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
        payload.put("provider", CaptureAiProvider.DEEPSEEK.name());
        payload.put("request_type", "CAPTURE_ANALYSIS");
        payload.put("source_type", request.sourceType().name());
        if (model != null) {
            payload.put("model", model);
        }
        return payload;
    }

    private CapturePipelineException llmFailure(AnalyzeRequest request, String model, String message, Exception cause) {
        String fullMessage = "provider=DEEPSEEK model=" + model + " " + message;
        if (cause == null) {
            return new CapturePipelineException(CaptureFailureReason.LLM_CALL_FAILED, fullMessage);
        }
        return new CapturePipelineException(CaptureFailureReason.LLM_CALL_FAILED, fullMessage, cause);
    }

    private int durationMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }
}
