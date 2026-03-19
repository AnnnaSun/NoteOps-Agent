package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureAiProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class RoutingCaptureAnalysisClient implements CaptureAnalysisClient {

    private final CaptureAiProperties properties;
    private final Map<CaptureAiProvider, CaptureProviderClient> providerClients;

    public RoutingCaptureAnalysisClient(CaptureAiProperties properties,
                                        List<CaptureProviderClient> providerClients) {
        this.properties = properties;
        this.providerClients = indexByProvider(providerClients);
    }

    @Override
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        CaptureProviderClient providerClient = providerClients.get(properties.provider());
        if (providerClient == null) {
            throw new IllegalStateException("capture ai provider is not registered: " + properties.provider());
        }
        return providerClient.analyze(request);
    }

    private Map<CaptureAiProvider, CaptureProviderClient> indexByProvider(List<CaptureProviderClient> clients) {
        Map<CaptureAiProvider, CaptureProviderClient> value = new EnumMap<>(CaptureAiProvider.class);
        for (CaptureProviderClient client : clients) {
            CaptureProviderClient duplicate = value.putIfAbsent(client.provider(), client);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate capture ai provider client: " + client.provider());
            }
        }
        return value;
    }
}
