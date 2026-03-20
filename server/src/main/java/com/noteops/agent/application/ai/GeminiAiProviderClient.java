package com.noteops.agent.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class GeminiAiProviderClient extends AbstractOpenAiCompatibleAiProviderClient {

    private static final String CLIENT_IDENTIFICATION = "noteops-agent/0.0.1";

    private final AiProperties properties;

    @Autowired
    public GeminiAiProviderClient(ObjectMapper objectMapper,
                                  AiProperties properties,
                                  ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    GeminiAiProviderClient(HttpClient httpClient,
                           ObjectMapper objectMapper,
                           AiProperties properties,
                           ToolInvocationLogRepository toolInvocationLogRepository) {
        super(httpClient, objectMapper, toolInvocationLogRepository);
        this.properties = properties;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    protected String clientName() {
        return "GeminiAiProviderClient";
    }

    @Override
    protected String errorCodePrefix() {
        return "GEMINI";
    }

    @Override
    protected String baseUrl() {
        return properties.gemini().baseUrl();
    }

    @Override
    protected String apiKey() {
        return properties.gemini().apiKey();
    }

    @Override
    protected Duration requestTimeout() {
        return properties.requestTimeout();
    }

    @Override
    protected Map<String, String> extraHeaders() {
        return Map.of("x-goog-api-client", CLIENT_IDENTIFICATION);
    }
}
