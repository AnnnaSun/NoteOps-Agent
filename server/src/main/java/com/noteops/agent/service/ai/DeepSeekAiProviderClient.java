package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class DeepSeekAiProviderClient extends AbstractOpenAiCompatibleAiProviderClient {

    private final AiProperties properties;

    @Autowired
    public DeepSeekAiProviderClient(ObjectMapper objectMapper,
                                    AiProperties properties,
                                    ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    DeepSeekAiProviderClient(HttpClient httpClient,
                             ObjectMapper objectMapper,
                             AiProperties properties,
                             ToolInvocationLogRepository toolInvocationLogRepository) {
        super(httpClient, objectMapper, toolInvocationLogRepository);
        this.properties = properties;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.DEEPSEEK;
    }

    @Override
    protected String clientName() {
        return "DeepSeekAiProviderClient";
    }

    @Override
    protected String errorCodePrefix() {
        return "DEEPSEEK";
    }

    @Override
    protected String baseUrl() {
        return properties.deepseek().baseUrl();
    }

    @Override
    protected String apiKey() {
        return properties.deepseek().apiKey();
    }

    @Override
    protected Duration requestTimeout() {
        return properties.requestTimeout();
    }
}
