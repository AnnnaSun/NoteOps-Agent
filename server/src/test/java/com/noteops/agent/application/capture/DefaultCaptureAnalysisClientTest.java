package com.noteops.agent.application.capture;

import com.noteops.agent.application.ai.AiClient;
import com.noteops.agent.application.ai.AiProvider;
import com.noteops.agent.application.ai.AiRequest;
import com.noteops.agent.application.ai.AiResponse;
import com.noteops.agent.domain.capture.CaptureInputType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCaptureAnalysisClientTest {

    @Test
    void buildsGenericAiRequestForCaptureRoute() {
        RecordingAiClient aiClient = new RecordingAiClient();
        DefaultCaptureAnalysisClient client = new DefaultCaptureAnalysisClient(aiClient);
        CaptureAnalysisClient.AnalyzeRequest request = new CaptureAnalysisClient.AnalyzeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            CaptureInputType.URL,
            "https://example.com",
            "hint",
            "Example title",
            "captured text"
        );

        CaptureAnalysisClient.AnalyzeResponse response = client.analyze(request);

        assertThat(response.provider()).isEqualTo(AiProvider.GEMINI);
        assertThat(response.model()).isEqualTo("gemini-test");
        assertThat(aiClient.lastRequest.routeKey()).isEqualTo(CaptureAnalysisClient.ROUTE_KEY);
        assertThat(aiClient.lastRequest.requestType()).isEqualTo(CaptureAnalysisClient.REQUEST_TYPE);
        assertThat(aiClient.lastRequest.toolName()).isEqualTo(CaptureAnalysisClient.TOOL_NAME);
        assertThat(aiClient.lastRequest.inputMetadata())
            .containsEntry("source_type", "URL")
            .containsEntry("source_url", "https://example.com");
        assertThat(aiClient.lastRequest.responseSchema()).containsKey("properties");
    }

    private static final class RecordingAiClient implements AiClient {

        private AiRequest lastRequest;

        @Override
        public AiResponse analyze(AiRequest request) {
            lastRequest = request;
            return new AiResponse(AiProvider.GEMINI, "gemini-test", "{\"summary\":\"ok\"}", 12);
        }
    }
}
