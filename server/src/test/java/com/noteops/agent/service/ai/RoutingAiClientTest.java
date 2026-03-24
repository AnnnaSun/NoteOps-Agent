package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;
import com.noteops.agent.service.capture.CapturePipelineException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingAiClientTest {

    @Test
    void routesByConfiguredRouteKey() {
        FakeProviderClient gatewayClient = new FakeProviderClient(AiProvider.OPENAI_COMPATIBLE);
        FakeProviderClient ollamaClient = new FakeProviderClient(AiProvider.OLLAMA);
        AiProperties properties = new AiProperties(
            AiProvider.OPENAI_COMPATIBLE,
            Duration.ofSeconds(20),
            Map.of(
                "capture-analysis", new AiProperties.Route(null, "gateway-route-model", null),
                "note-analysis", new AiProperties.Route(null, "note-route-model", null)
            ),
            new AiProperties.OpenAiCompatible(null, "https://gateway.example.com/v1", "gateway-key", "gateway-default-model", Map.of()),
            new AiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(gatewayClient, ollamaClient));
        AiRequest request = sampleRequest("note-analysis");
        AiResponse response = new AiResponse(AiProvider.OPENAI_COMPATIBLE, "note-route-model", "{}", 10);
        gatewayClient.response = response;

        AiResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(gatewayClient.lastRequest).isEqualTo(request);
        assertThat(gatewayClient.lastRoute).isEqualTo(new AiProperties.ResolvedRoute(AiProvider.OPENAI_COMPATIBLE, null, "note-route-model"));
    }

    @Test
    void passesConfiguredOpenAiEndpointToProvider() {
        FakeProviderClient gatewayClient = new FakeProviderClient(AiProvider.OPENAI_COMPATIBLE);
        AiProperties properties = new AiProperties(
            AiProvider.OPENAI_COMPATIBLE,
            Duration.ofSeconds(20),
            Map.of("search-enhancement", new AiProperties.Route("deepseek", "deepseek-r1", null)),
            new AiProperties.OpenAiCompatible(
                "kimi",
                "https://fallback.example.com/v1",
                "unused-key",
                "fallback-model",
                Map.of(
                    "kimi", new AiProperties.Endpoint("https://kimi.example.com/v1", "kimi-key", "kimi-k2"),
                    "deepseek", new AiProperties.Endpoint("https://deepseek.example.com/v1", "deepseek-key", "deepseek-chat")
                )
            ),
            new AiProperties.Ollama("http://localhost:11434", "llama-test")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(gatewayClient));
        AiRequest request = sampleRequest("search-enhancement");
        gatewayClient.response = new AiResponse(AiProvider.OPENAI_COMPATIBLE, "deepseek-r1", "{}", 10);

        client.analyze(request);

        assertThat(gatewayClient.lastRoute).isEqualTo(new AiProperties.ResolvedRoute(AiProvider.OPENAI_COMPATIBLE, "deepseek", "deepseek-r1"));
    }

    @Test
    void usesRouteProviderWhenExplicitlyConfigured() {
        FakeProviderClient gatewayClient = new FakeProviderClient(AiProvider.OPENAI_COMPATIBLE);
        FakeProviderClient ollamaClient = new FakeProviderClient(AiProvider.OLLAMA);
        AiProperties properties = new AiProperties(
            AiProvider.OPENAI_COMPATIBLE,
            Duration.ofSeconds(20),
            Map.of("capture-analysis", new AiProperties.Route(null, "ollama-route-model", AiProvider.OLLAMA)),
            new AiProperties.OpenAiCompatible(null, "https://gateway.example.com/v1", "gateway-key", "gateway-default-model", Map.of()),
            new AiProperties.Ollama("http://localhost:11434", "ollama-default-model")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(gatewayClient, ollamaClient));
        AiRequest request = sampleRequest("capture-analysis");
        AiResponse response = new AiResponse(AiProvider.OLLAMA, "ollama-route-model", "{}", 8);
        ollamaClient.response = response;

        AiResponse result = client.analyze(request);

        assertThat(result).isSameAs(response);
        assertThat(gatewayClient.lastRoute).isNull();
        assertThat(ollamaClient.lastRoute).isEqualTo(new AiProperties.ResolvedRoute(AiProvider.OLLAMA, null, "ollama-route-model"));
    }

    @Test
    void throwsWhenResolvedProviderClientIsMissing() {
        AiProperties properties = new AiProperties(
            AiProvider.OPENAI_COMPATIBLE,
            Duration.ofSeconds(20),
            Map.of("capture-analysis", new AiProperties.Route(null, "gateway-route-model", null)),
            new AiProperties.OpenAiCompatible(null, "https://gateway.example.com/v1", "gateway-key", "gateway-default-model", Map.of()),
            new AiProperties.Ollama("http://localhost:11434", "ollama-model")
        );
        RoutingAiClient client = new RoutingAiClient(properties, List.of(new FakeProviderClient(AiProvider.OLLAMA)));
        AiRequest request = sampleRequest("capture-analysis");

        assertThatThrownBy(() -> client.analyze(request))
            .isInstanceOf(CapturePipelineException.class)
            .hasMessageContaining("no ai provider client is available");
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
            null
        );
    }

    private static final class FakeProviderClient implements AiProviderClient {

        private final AiProvider provider;
        private AiResponse response;
        private CapturePipelineException failure;
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
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
