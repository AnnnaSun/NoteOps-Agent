package com.noteops.agent.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.application.capture.CapturePipelineException;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractOpenAiCompatibleAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenAiCompatibleAiProviderClient.class);
    private static final Map<String, String> NO_EXTRA_HEADERS = Map.of();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ToolInvocationLogRepository toolInvocationLogRepository;

    protected AbstractOpenAiCompatibleAiProviderClient(HttpClient httpClient,
                                                       ObjectMapper objectMapper,
                                                       ToolInvocationLogRepository toolInvocationLogRepository) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
    }

    @Override
    public AiResponse analyze(AiRequest request, AiProperties.ResolvedRoute route) {
        String model = route.model();
        String apiKey = apiKey();
        if (model == null || apiKey == null) {
            throw llmFailure(model, provider().name().toLowerCase() + " provider is missing api key or model", null);
        }

        long startedAt = System.nanoTime();
        log.info(
            "module={} action=llm_call_start result=RUNNING trace_id={} user_id={} route_key={} provider={} model={}",
            clientName(),
            request.traceId(),
            request.userId(),
            request.routeKey(),
            provider(),
            model
        );
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
            ));
            if (request.responseMode() == AiResponseMode.JSON_OBJECT) {
                requestBody.put("response_format", Map.of("type", "json_object"));
            }
            addRequestBodyOverrides(request, route, requestBody);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(resolveEndpoint(baseUrl(), endpointPath())))
                .timeout(requestTimeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
            for (Map.Entry<String, String> header : extraHeaders().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }

            HttpResponse<String> response = httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody))).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            int durationMs = durationMs(startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                appendFailureLog(request, model, durationMs, errorCodePrefix() + "_HTTP_" + response.statusCode(), response.body());
                throw llmFailure(model, provider().name().toLowerCase() + " call failed with status " + response.statusCode(), null);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode contentNode = responseJson.path("choices").path(0).path("message").path("content");
            String content = contentNode.asText(null);
            if (content == null || content.isBlank()) {
                appendFailureLog(request, model, durationMs, errorCodePrefix() + "_EMPTY_RESPONSE", provider().name().toLowerCase() + " response content is empty");
                throw llmFailure(model, provider().name().toLowerCase() + " response content is empty", null);
            }

            appendSuccessLog(request, model, durationMs, content);
            log.info(
                "module={} action=llm_call_end result=SUCCESS trace_id={} user_id={} route_key={} provider={} model={} duration_ms={}",
                clientName(),
                request.traceId(),
                request.userId(),
                request.routeKey(),
                provider(),
                model,
                durationMs
            );
            return new AiResponse(provider(), model, content, durationMs);
        } catch (CapturePipelineException exception) {
            log.warn(
                "module={} action=llm_call_end result=FAILED trace_id={} user_id={} route_key={} provider={} model={} error_code={} error_message={}",
                clientName(),
                request.traceId(),
                request.userId(),
                request.routeKey(),
                provider(),
                model,
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            int durationMs = durationMs(startedAt);
            appendFailureLog(request, model, durationMs, errorCodePrefix() + "_CALL_ERROR", exception.getMessage());
            log.warn(
                "module={} action=llm_call_end result=FAILED trace_id={} user_id={} route_key={} provider={} model={} duration_ms={} error_code={} error_message={}",
                clientName(),
                request.traceId(),
                request.userId(),
                request.routeKey(),
                provider(),
                model,
                durationMs,
                CaptureFailureReason.LLM_CALL_FAILED.name(),
                exception.getMessage()
            );
            throw llmFailure(model, provider().name().toLowerCase() + " call failed: " + exception.getMessage(), exception);
        }
    }

    protected void addRequestBodyOverrides(AiRequest request,
                                           AiProperties.ResolvedRoute route,
                                           Map<String, Object> requestBody) {
    }

    protected Map<String, String> extraHeaders() {
        return NO_EXTRA_HEADERS;
    }

    protected String endpointPath() {
        return "/chat/completions";
    }

    protected abstract String clientName();

    protected abstract String errorCodePrefix();

    protected abstract String baseUrl();

    protected abstract String apiKey();

    protected abstract java.time.Duration requestTimeout();

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
        payload.put("provider", provider().name());
        payload.put("request_type", request.requestType());
        payload.put("route_key", request.routeKey());
        if (model != null) {
            payload.put("model", model);
        }
        payload.putAll(request.inputMetadata());
        return payload;
    }

    private CapturePipelineException llmFailure(String model, String message, Exception cause) {
        String fullMessage = "provider=" + provider().name() + " model=" + model + " " + message;
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
        return baseToolName + "." + provider().name().toLowerCase();
    }

    private String resolveEndpoint(String baseUrl, String endpointPath) {
        String normalizedBaseUrl = stripTrailingSlash(baseUrl);
        String normalizedPath = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
        return normalizedBaseUrl + normalizedPath;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
