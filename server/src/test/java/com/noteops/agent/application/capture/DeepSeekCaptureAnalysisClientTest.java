package com.noteops.agent.application.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.domain.capture.CaptureAiProvider;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.domain.capture.CaptureInputType;
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

class DeepSeekCaptureAnalysisClientTest {

    @Test
    void throwsLlmCallFailedWhenProviderConfigIsMissing() {
        DeepSeekCaptureAnalysisClient client = new DeepSeekCaptureAnalysisClient(
            mock(HttpClient.class),
            new ObjectMapper(),
            new CaptureAiProperties(
                CaptureAiProvider.DEEPSEEK,
                Duration.ofSeconds(20),
                new CaptureAiProperties.DeepSeek("https://api.deepseek.com", null, null),
                new CaptureAiProperties.Ollama("http://localhost:11434", "llama-test")
            ),
            new RecordingToolInvocationLogRepository()
        );

        assertThatThrownBy(() -> client.analyze(sampleRequest()))
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
        DeepSeekCaptureAnalysisClient client = new DeepSeekCaptureAnalysisClient(
            httpClient,
            new ObjectMapper(),
            new CaptureAiProperties(
                CaptureAiProvider.DEEPSEEK,
                Duration.ofSeconds(20),
                new CaptureAiProperties.DeepSeek("https://api.deepseek.com", "key", "deepseek-chat"),
                new CaptureAiProperties.Ollama("http://localhost:11434", "llama-test")
            ),
            toolLogRepository
        );

        assertThatThrownBy(() -> client.analyze(sampleRequest()))
            .isInstanceOf(CapturePipelineException.class)
            .satisfies(exception -> assertThat(((CapturePipelineException) exception).failureReason())
                .isEqualTo(CaptureFailureReason.LLM_CALL_FAILED))
            .hasMessageContaining("status 502");

        assertThat(toolLogRepository.statuses).containsExactly("FAILED");
        assertThat(toolLogRepository.errorCodes).containsExactly("DEEPSEEK_HTTP_502");
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private CaptureAnalysisClient.AnalyzeRequest sampleRequest() {
        return new CaptureAnalysisClient.AnalyzeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            CaptureInputType.TEXT,
            null,
            null,
            null,
            "captured text"
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
