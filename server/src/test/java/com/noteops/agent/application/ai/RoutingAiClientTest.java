package com.noteops.agent.application.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingAiClientTest {

    @Test
    void routesByConfiguredRouteKey() {
        FakeProviderClient deepSeekClient = new FakeProviderClient(AiProvider.DEEPSEEK);
        FakeProviderClient kimiClient = new FakeProviderClient(AiProvider.KIMI);
        FakeProviderClient geminiClient = new FakeProviderClient(AiProvider.GEMINI);
        FakeProviderClient ollamaClient = new FakeProviderClient(AiProvider.OLLAMA);
        AiProperties properties = new AiProperties(
            AiProvider.DEEPSEEK,
            Duration.ofSeconds(20),
            Map.of(
                "capture-analysis", new AiProperties.Route(AiProvider.KIMI, "kimi-route-model"),
                "note-analysis", new AiProperties.Route(AiProvider.GEMINI, "gemini-route-model")
            ),
            new AiProperties.DeepSeek("https://api.deepseek.com", "deepseek-key", "deepseek-chat"),
            new AiProperties.Kimi("https://api.moonshot.cn/v1", "kimi-key", "kimi-model"),
            new AiProperties.Gemini("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-key", "gemini-model"),
            new AiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(deepSeekClient, kimiClient, geminiClient, ollamaClient));
        AiRequest request = sampleRequest("note-analysis");
        AiResponse response = new AiResponse(AiProvider.GEMINI, "gemini-route-model", "{}", 10);
        geminiClient.response = response;

        AiResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(geminiClient.lastRequest).isEqualTo(request);
        assertThat(geminiClient.lastRoute).isEqualTo(new AiProperties.ResolvedRoute(AiProvider.GEMINI, "gemini-route-model"));
    }

    @Test
    void usesProviderOverrideWhenProvided() {
        FakeProviderClient deepSeekClient = new FakeProviderClient(AiProvider.DEEPSEEK);
        FakeProviderClient kimiClient = new FakeProviderClient(AiProvider.KIMI);
        AiProperties properties = new AiProperties(
            AiProvider.DEEPSEEK,
            Duration.ofSeconds(20),
            Map.of("capture-analysis", new AiProperties.Route(AiProvider.DEEPSEEK, "deepseek-chat")),
            new AiProperties.DeepSeek("https://api.deepseek.com", "deepseek-key", "deepseek-chat"),
            new AiProperties.Kimi("https://api.moonshot.cn/v1", "kimi-key", "kimi-model"),
            new AiProperties.Gemini("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-key", "gemini-model"),
            new AiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(deepSeekClient, kimiClient));
        AiRequest request = new AiRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "capture-analysis",
            "CAPTURE_ANALYSIS",
            "capture.analysis",
            "system",
            "user",
            AiResponseMode.JSON_OBJECT,
            Map.of(),
            Map.of(),
            AiProvider.KIMI,
            "kimi-override-model"
        );
        AiResponse response = new AiResponse(AiProvider.KIMI, "kimi-override-model", "{}", 10);
        kimiClient.response = response;

        AiResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(kimiClient.lastRoute).isEqualTo(new AiProperties.ResolvedRoute(AiProvider.KIMI, "kimi-override-model"));
    }

    private AiRequest sampleRequest(String routeKey) {
        return new AiRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            routeKey,
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

    private static final class FakeProviderClient implements AiProviderClient {

        private final AiProvider provider;
        private AiResponse response;
        private AiRequest lastRequest;
        private AiProperties.ResolvedRoute lastRoute;

        private FakeProviderClient(AiProvider provider) {
            this.provider = provider;
        }

        @Override
        public AiProvider provider() {
            return provider;
        }

        @Override
        public AiResponse analyze(AiRequest request, AiProperties.ResolvedRoute route) {
            lastRequest = request;
            lastRoute = route;
            return response;
        }
    }
}
