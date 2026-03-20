package com.noteops.agent.application.ai;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class RoutingAiClient implements AiClient {

    private final AiProperties properties;
    private final Map<AiProvider, AiProviderClient> providerClients;

    public RoutingAiClient(AiProperties properties,
                           List<AiProviderClient> providerClients) {
        this.properties = properties;
        this.providerClients = indexByProvider(providerClients);
    }

    @Override
    public AiResponse analyze(AiRequest request) {
        AiProperties.ResolvedRoute route = properties.resolveRoute(
            request.routeKey(),
            request.providerOverride(),
            request.modelOverride()
        );
        AiProviderClient providerClient = providerClients.get(route.provider());
        if (providerClient == null) {
            throw new IllegalStateException("ai provider is not registered: " + route.provider());
        }
        return providerClient.analyze(request, route);
    }

    private Map<AiProvider, AiProviderClient> indexByProvider(List<AiProviderClient> clients) {
        Map<AiProvider, AiProviderClient> value = new EnumMap<>(AiProvider.class);
        for (AiProviderClient client : clients) {
            AiProviderClient duplicate = value.putIfAbsent(client.provider(), client);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate ai provider client: " + client.provider());
            }
        }
        return value;
    }
}
