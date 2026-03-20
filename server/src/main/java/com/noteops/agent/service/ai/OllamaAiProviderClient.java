package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.service.capture.CapturePipelineException;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
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
public class OllamaAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiProviderClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final ToolInvocationLogRepository toolInvocationLogRepository;

    @Autowired
    public OllamaAiProviderClient(ObjectMapper objectMapper,
                                  AiProperties properties,
                                  ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    OllamaAiProviderClient(HttpClient httpClient,
                           ObjectMapper objectMapper,
                           AiProperties properties,
                           ToolInvocationLogRepository toolInvocationLogRepository) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }

    @Override
    public AiResponse analyze(AiRequest request, AiProperties.ResolvedRoute route) {
        String model = route.model();
        if (model == null) {
            throw llmFailure(model, "ollama provider is missing model", null);
        }

        long startedAt = System.nanoTime();
        log.info(
            "module=OllamaAiProviderClient action=llm_call_start result=RUNNING trace_id={} user_id={} route_key={} provider={} model={}",
            request.traceId(),
            request.userId(),
            request.routeKey(),
            AiProvider.OLLAMA,
            model
        );
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
            ));
            if (request.responseMode() == AiResponseMode.JSON_OBJECT) {
                requestBody.put("format", request.responseSchema().isEmpty() ? "json" : request.responseSchema());
            }

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
                "module=OllamaAiProviderClient action=llm_call_end result=SUCCESS trace_id={} user_id={} route_key={} provider={} model={} duration_ms={}",
                request.traceId(),
                request.userId(),
                request.routeKey(),
                AiProvider.OLLAMA,
                model,
                durationMs
            );
            return new AiResponse(AiProvider.OLLAMA, model, content, durationMs);
        } catch (CapturePipelineException exception) {
            log.warn(
                "module=OllamaAiProviderClient action=llm_call_end result=FAILED trace_id={} user_id={} route_key={} provider={} model={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                request.routeKey(),
                AiProvider.OLLAMA,
                model,
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            int durationMs = durationMs(startedAt);
            appendFailureLog(request, model, durationMs, "OLLAMA_CALL_ERROR", exception.getMessage());
            log.warn(
                "module=OllamaAiProviderClient action=llm_call_end result=FAILED trace_id={} user_id={} route_key={} provider={} model={} duration_ms={} error_code={} error_message={}",
                request.traceId(),
                request.userId(),
                request.routeKey(),
                AiProvider.OLLAMA,
                model,
                durationMs,
                CaptureFailureReason.LLM_CALL_FAILED.name(),
                exception.getMessage()
            );
            throw llmFailure(model, "ollama call failed: " + exception.getMessage(), exception);
        }
    }

    private void appendSuccessLog(AiRequest request, String model, int durationMs, String content) {
        toolInvocationLogRepository.append(
            request.userId(),
            request.traceId(),
            toolName(request),
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

    private void appendFailureLog(AiRequest request, String model, int durationMs, String errorCode, String errorMessage) {
        toolInvocationLogRepository.append(
            request.userId(),
            request.traceId(),
            toolName(request),
            "FAILED",
            inputDigest(model, request),
            Map.of("result", "FAILED"),
            durationMs,
            errorCode,
            errorMessage
        );
    }

    private Map<String, Object> inputDigest(String model, AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", AiProvider.OLLAMA.name());
        payload.put("request_type", request.requestType());
        payload.put("route_key", request.routeKey());
        if (model != null) {
            payload.put("model", model);
        }
        payload.putAll(request.inputMetadata());
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

    private String toolName(AiRequest request) {
        String baseToolName = request.toolName() == null ? "ai.analysis" : request.toolName();
        return baseToolName + ".ollama";
    }
}
