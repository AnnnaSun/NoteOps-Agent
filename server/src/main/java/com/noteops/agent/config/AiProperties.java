package com.noteops.agent.config;

import com.noteops.agent.service.ai.AiProvider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "noteops.ai")
public record AiProperties(
    AiProvider defaultProvider,
    Duration requestTimeout,
    Map<String, Route> routes,
    OpenAiCompatible openAiCompatible,
    Ollama ollama
) {

    public AiProperties {
        defaultProvider = defaultProvider == null ? AiProvider.OPENAI_COMPATIBLE : defaultProvider;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        routes = routes == null ? Map.of() : Map.copyOf(routes);
        openAiCompatible = openAiCompatible == null
            ? new OpenAiCompatible(null, "http://localhost:11434/v1", null, null, Map.of())
            : openAiCompatible;
        ollama = ollama == null ? new Ollama("http://localhost:11434", null) : ollama;
    }

    public ResolvedRoute resolveRoute(String routeKey, String modelOverride) {
        Route route = routeKey == null ? null : routes.get(routeKey);
        AiProvider provider = route != null && route.provider() != null
            ? route.provider()
            : defaultProvider;
        String endpoint = route == null ? null : route.endpoint();
        String model = blankToNull(modelOverride);
        if (model == null && route != null) {
            model = route.model();
        }
        if (model == null) {
            model = defaultModel(provider);
        }
        return new ResolvedRoute(provider, blankToNull(endpoint), blankToNull(model));
    }

    public String defaultModel(AiProvider provider) {
        return switch (provider) {
            case OPENAI_COMPATIBLE -> resolveOpenAiCompatibleEndpoint(null).model();
            case OLLAMA -> ollama.model();
        };
    }

    public OpenAiCompatibleEndpoint resolveOpenAiCompatibleEndpoint(String endpointKey) {
        return openAiCompatible.resolveEndpoint(endpointKey);
    }

    public record Route(
        String endpoint,
        String model,
        AiProvider provider
    ) {

        public Route {
            endpoint = blankToNull(endpoint);
            model = blankToNull(model);
        }
    }

    public record ResolvedRoute(
        AiProvider provider,
        String endpoint,
        String model
    ) {
    }

    public record OpenAiCompatible(
        String defaultEndpoint,
        String baseUrl,
        String apiKey,
        String model,
        Map<String, Endpoint> endpoints
    ) {

        public OpenAiCompatible {
            // 这里的配置语义是“兼容 OpenAI chat completions 的统一网关”。
            // 如果上游暴露的是非兼容协议，不要继续塞进这个配置块。
            defaultEndpoint = blankToNull(defaultEndpoint);
            baseUrl = blankToNull(baseUrl) == null ? "http://localhost:11434/v1" : baseUrl.trim();
            apiKey = blankToNull(apiKey);
            model = blankToNull(model);
            endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
        }

        OpenAiCompatibleEndpoint resolveEndpoint(String endpointKey) {
            String normalizedEndpoint = blankToNull(endpointKey);
            if (!endpoints.isEmpty()) {
                String resolvedKey = normalizedEndpoint;
                if (resolvedKey == null) {
                    resolvedKey = defaultEndpoint != null ? defaultEndpoint : endpoints.keySet().stream().findFirst().orElse(null);
                }
                Endpoint endpoint = resolvedKey == null ? null : endpoints.get(resolvedKey);
                if (endpoint != null) {
                    return new OpenAiCompatibleEndpoint(resolvedKey, endpoint.baseUrl(), endpoint.apiKey(), endpoint.model());
                }
            }
            return new OpenAiCompatibleEndpoint("default", baseUrl, apiKey, model);
        }
    }

    public record Endpoint(
        String baseUrl,
        String apiKey,
        String model
    ) {

        public Endpoint {
            baseUrl = blankToNull(baseUrl) == null ? "http://localhost:11434/v1" : baseUrl.trim();
            apiKey = blankToNull(apiKey);
            model = blankToNull(model);
        }
    }

    public record OpenAiCompatibleEndpoint(
        String name,
        String baseUrl,
        String apiKey,
        String model
    ) {
    }

    public record Ollama(
        String baseUrl,
        String model
    ) {

        public Ollama {
            baseUrl = blankToNull(baseUrl) == null ? "http://localhost:11434" : baseUrl.trim();
            model = blankToNull(model);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
