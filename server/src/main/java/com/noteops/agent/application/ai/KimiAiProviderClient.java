package com.noteops.agent.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class KimiAiProviderClient extends AbstractOpenAiCompatibleAiProviderClient {

    private final AiProperties properties;

    @Autowired
    public KimiAiProviderClient(ObjectMapper objectMapper,
                                AiProperties properties,
                                ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    KimiAiProviderClient(HttpClient httpClient,
                         ObjectMapper objectMapper,
                         AiProperties properties,
                         ToolInvocationLogRepository toolInvocationLogRepository) {
        super(httpClient, objectMapper, toolInvocationLogRepository);
        this.properties = properties;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.KIMI;
    }

    @Override
    protected String clientName() {
        return "KimiAiProviderClient";
    }

    @Override
    protected String errorCodePrefix() {
        return "KIMI";
    }

    @Override
    protected String baseUrl() {
        return properties.kimi().baseUrl();
    }

    @Override
    protected String apiKey() {
        return properties.kimi().apiKey();
    }

    @Override
    protected Duration requestTimeout() {
        return properties.requestTimeout();
    }
}
