package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureAiProvider;
import com.noteops.agent.domain.capture.CaptureInputType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingCaptureAnalysisClientTest {

    @Test
    void routesToDeepSeekWhenConfigured() {
        FakeProviderClient deepSeekClient = new FakeProviderClient(CaptureAiProvider.DEEPSEEK);
        FakeProviderClient ollamaClient = new FakeProviderClient(CaptureAiProvider.OLLAMA);
        CaptureAiProperties properties = new CaptureAiProperties(
            CaptureAiProvider.DEEPSEEK,
            Duration.ofSeconds(20),
            new CaptureAiProperties.DeepSeek("https://api.deepseek.com", "key", "deepseek-chat"),
            new CaptureAiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingCaptureAnalysisClient client = new RoutingCaptureAnalysisClient(properties, List.of(deepSeekClient, ollamaClient));
        CaptureAnalysisClient.AnalyzeRequest request = new CaptureAnalysisClient.AnalyzeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            CaptureInputType.TEXT,
            null,
            null,
            null,
            "captured text"
        );
        CaptureAnalysisClient.AnalyzeResponse response = new CaptureAnalysisClient.AnalyzeResponse(
            CaptureAiProvider.DEEPSEEK,
            "deepseek-chat",
            "{}",
            10
        );
        deepSeekClient.response = response;

        CaptureAnalysisClient.AnalyzeResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(deepSeekClient.lastRequest).isEqualTo(request);
    }

    @Test
    void routesToOllamaWhenConfigured() {
        FakeProviderClient deepSeekClient = new FakeProviderClient(CaptureAiProvider.DEEPSEEK);
        FakeProviderClient ollamaClient = new FakeProviderClient(CaptureAiProvider.OLLAMA);
        CaptureAiProperties properties = new CaptureAiProperties(
            CaptureAiProvider.OLLAMA,
            Duration.ofSeconds(20),
            new CaptureAiProperties.DeepSeek("https://api.deepseek.com", "key", "deepseek-chat"),
            new CaptureAiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingCaptureAnalysisClient client = new RoutingCaptureAnalysisClient(properties, List.of(deepSeekClient, ollamaClient));
        CaptureAnalysisClient.AnalyzeRequest request = new CaptureAnalysisClient.AnalyzeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            CaptureInputType.URL,
            "https://example.com",
            null,
            "Example",
            "captured text"
        );
        CaptureAnalysisClient.AnalyzeResponse response = new CaptureAnalysisClient.AnalyzeResponse(
            CaptureAiProvider.OLLAMA,
            "llama-test",
            "{}",
            10
        );
        ollamaClient.response = response;

        CaptureAnalysisClient.AnalyzeResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(ollamaClient.lastRequest).isEqualTo(request);
    }

    private static final class FakeProviderClient implements CaptureProviderClient {

        private final CaptureAiProvider provider;
        private CaptureAnalysisClient.AnalyzeResponse response;
        private CaptureAnalysisClient.AnalyzeRequest lastRequest;

        private FakeProviderClient(CaptureAiProvider provider) {
            this.provider = provider;
        }

        @Override
        public CaptureAiProvider provider() {
            return provider;
        }

        @Override
        public AnalyzeResponse analyze(AnalyzeRequest request) {
            lastRequest = request;
            return response;
        }
    }
}
