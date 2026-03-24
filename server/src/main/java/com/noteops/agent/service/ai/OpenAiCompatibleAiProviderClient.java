package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class OpenAiCompatibleAiProviderClient extends AbstractOpenAiCompatibleAiProviderClient {

    private final AiProperties properties;

    @Autowired
    public OpenAiCompatibleAiProviderClient(ObjectMapper objectMapper,
                                            AiProperties properties,
                                            ToolInvocationLogRepository toolInvocationLogRepository) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, properties, toolInvocationLogRepository);
    }

    OpenAiCompatibleAiProviderClient(HttpClient httpClient,
                                     ObjectMapper objectMapper,
                                     AiProperties properties,
                                     ToolInvocationLogRepository toolInvocationLogRepository) {
        super(httpClient, objectMapper, toolInvocationLogRepository);
        this.properties = properties;
    }

    @Override
    // 这里只负责把统一网关或兼容网关映射到 OpenAI-compatible 协议调用。
    public AiProvider provider() {
        return AiProvider.OPENAI_COMPATIBLE;
    }

    @Override
    protected String clientName() {
        return "OpenAiCompatibleAiProviderClient";
    }

    @Override
    protected String errorCodePrefix() {
        return "OPENAI_COMPATIBLE";
    }

    @Override
    protected String baseUrl(AiProperties.ResolvedRoute route) {
        return properties.resolveOpenAiCompatibleEndpoint(route.endpoint()).baseUrl();
    }

    @Override
    protected String apiKey(AiProperties.ResolvedRoute route) {
        return properties.resolveOpenAiCompatibleEndpoint(route.endpoint()).apiKey();
    }

    @Override
    protected Duration requestTimeout() {
        return properties.requestTimeout();
    }
}
