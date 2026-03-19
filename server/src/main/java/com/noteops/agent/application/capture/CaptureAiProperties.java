package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureAiProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "noteops.capture.ai")
public record CaptureAiProperties(
    CaptureAiProvider provider,
    Duration requestTimeout,
    DeepSeek deepseek,
    Ollama ollama
) {

    public CaptureAiProperties {
        provider = provider == null ? CaptureAiProvider.DEEPSEEK : provider;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        deepseek = deepseek == null ? new DeepSeek("https://api.deepseek.com", null, null) : deepseek;
        ollama = ollama == null ? new Ollama("http://localhost:11434", null) : ollama;
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
