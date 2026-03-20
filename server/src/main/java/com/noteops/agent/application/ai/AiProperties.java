package com.noteops.agent.application.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "noteops.ai")
public record AiProperties(
    AiProvider defaultProvider,
    Duration requestTimeout,
    Map<String, Route> routes,
    DeepSeek deepseek,
    Kimi kimi,
    Gemini gemini,
    Ollama ollama
) {

    public AiProperties {
        defaultProvider = defaultProvider == null ? AiProvider.DEEPSEEK : defaultProvider;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        routes = routes == null ? Map.of() : Map.copyOf(routes);
        deepseek = deepseek == null ? new DeepSeek("https://api.deepseek.com", null, null) : deepseek;
        kimi = kimi == null ? new Kimi("https://api.moonshot.cn/v1", null, null) : kimi;
        gemini = gemini == null ? new Gemini("https://generativelanguage.googleapis.com/v1beta/openai", null, null) : gemini;
        ollama = ollama == null ? new Ollama("http://localhost:11434", null) : ollama;
    }

    public ResolvedRoute resolveRoute(String routeKey, AiProvider providerOverride, String modelOverride) {
        Route route = routeKey == null ? null : routes.get(routeKey);
        AiProvider provider = providerOverride != null
            ? providerOverride
            : route != null && route.provider() != null
                ? route.provider()
                : defaultProvider;
        String model = blankToNull(modelOverride);
        if (model == null && route != null) {
            model = route.model();
        }
        if (model == null) {
            model = defaultModel(provider);
        }
        return new ResolvedRoute(provider, model);
    }

    public String defaultModel(AiProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> deepseek.model();
            case KIMI -> kimi.model();
            case GEMINI -> gemini.model();
            case OLLAMA -> ollama.model();
        };
    }

    public record Route(
        AiProvider provider,
        String model
    ) {

        public Route {
            model = blankToNull(model);
        }
    }

    public record ResolvedRoute(
        AiProvider provider,
        String model
    ) {
    }

    public record DeepSeek(
        String baseUrl,
        String apiKey,
        String model
    ) {

        public DeepSeek {
            baseUrl = blankToNull(baseUrl) == null ? "https://api.deepseek.com" : baseUrl.trim();
            apiKey = blankToNull(apiKey);
            model = blankToNull(model);
        }
    }

    public record Kimi(
        String baseUrl,
        String apiKey,
        String model
    ) {

        public Kimi {
            baseUrl = blankToNull(baseUrl) == null ? "https://api.moonshot.cn/v1" : baseUrl.trim();
            apiKey = blankToNull(apiKey);
            model = blankToNull(model);
        }
    }

    public record Gemini(
        String baseUrl,
        String apiKey,
        String model
    ) {

        public Gemini {
            baseUrl = blankToNull(baseUrl) == null ? "https://generativelanguage.googleapis.com/v1beta/openai" : baseUrl.trim();
            apiKey = blankToNull(apiKey);
            model = blankToNull(model);
        }
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
