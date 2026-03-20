package com.noteops.agent.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.application.capture.CapturePipelineException;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GeminiAiProviderClientTest {

    @Test
    void throwsLlmCallFailedWhenProviderConfigIsMissing() {
        GeminiAiProviderClient client = new GeminiAiProviderClient(
            mock(HttpClient.class),
            new ObjectMapper(),
            new AiProperties(
                AiProvider.GEMINI,
                Duration.ofSeconds(20),
                Map.of(),
                new AiProperties.DeepSeek("https://api.deepseek.com", "deepseek-key", "deepseek-chat"),
                new AiProperties.Kimi("https://api.moonshot.cn/v1", "kimi-key", "kimi-model"),
                new AiProperties.Gemini("https://generativelanguage.googleapis.com/v1beta/openai", null, null),
                new AiProperties.Ollama("http://localhost:11434", "llama-test")
            ),
            new RecordingToolInvocationLogRepository()
        );

        assertThatThrownBy(() -> client.analyze(sampleRequest(), new AiProperties.ResolvedRoute(AiProvider.GEMINI, null)))
            .isInstanceOf(CapturePipelineException.class)
            .satisfies(exception -> assertThat(((CapturePipelineException) exception).failureReason())
                .isEqualTo(CaptureFailureReason.LLM_CALL_FAILED));
    }

    @Test
    void mapsProviderHttpFailureToLlmCallFailedAndWritesToolLog() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        doReturn(502).when(response).statusCode();
        doReturn("upstream failure").when(response).body();
        RecordingToolInvocationLogRepository toolLogRepository = new RecordingToolInvocationLogRepository();
        GeminiAiProviderClient client = new GeminiAiProviderClient(
            httpClient,
            new ObjectMapper(),
            new AiProperties(
                AiProvider.GEMINI,
                Duration.ofSeconds(20),
                Map.of(),
                new AiProperties.DeepSeek("https://api.deepseek.com", "deepseek-key", "deepseek-chat"),
                new AiProperties.Kimi("https://api.moonshot.cn/v1", "kimi-key", "kimi-model"),
                new AiProperties.Gemini("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-key", "gemini-model"),
                new AiProperties.Ollama("http://localhost:11434", "llama-test")
            ),
            toolLogRepository
        );

        assertThatThrownBy(() -> client.analyze(sampleRequest(), new AiProperties.ResolvedRoute(AiProvider.GEMINI, "gemini-model")))
            .isInstanceOf(CapturePipelineException.class)
            .satisfies(exception -> assertThat(((CapturePipelineException) exception).failureReason())
                .isEqualTo(CaptureFailureReason.LLM_CALL_FAILED))
            .hasMessageContaining("status 502");

        assertThat(toolLogRepository.statuses).containsExactly("FAILED");
        assertThat(toolLogRepository.errorCodes).containsExactly("GEMINI_HTTP_502");
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private AiRequest sampleRequest() {
        return new AiRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "capture-analysis",
            "CAPTURE_ANALYSIS",
            "capture.analysis",
            "system",
            "user",
            AiResponseMode.JSON_OBJECT,
            Map.of(),
            Map.of("source_type", "TEXT"),
            null,
            null
        );
    }

    private static final class RecordingToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final java.util.List<String> statuses = new java.util.ArrayList<>();
        private final java.util.List<String> errorCodes = new java.util.ArrayList<>();

        @Override
        public void append(UUID userId,
                           UUID traceId,
                           String toolName,
                           String status,
                           Map<String, Object> inputDigest,
                           Map<String, Object> outputDigest,
                           Integer latencyMs,
                           String errorCode,
                           String errorMessage) {
            statuses.add(status);
            if (errorCode != null) {
                errorCodes.add(errorCode);
            }
        }
    }
}
