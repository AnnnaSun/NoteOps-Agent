package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.service.capture.CapturePipelineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class RoutingAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingAiClient.class);

    private final AiProperties properties;
    private final Map<AiProvider, AiProviderClient> providerClients;

    public RoutingAiClient(AiProperties properties,
                           List<AiProviderClient> providerClients) {
        this.properties = properties;
        this.providerClients = indexByProvider(providerClients);
    }

    @Override
    // 统一网关场景下，每个 route 只解析一个目标 provider/model，不再做跨 provider fallback。
    public AiResponse analyze(AiRequest request) {
        AiProperties.ResolvedRoute route = properties.resolveRoute(
            request.routeKey(),
            request.modelOverride()
        );
        AiProviderClient providerClient = providerClients.get(route.provider());
        if (providerClient != null) {
            return providerClient.analyze(request, route);
        }
        log.warn(
            "module=RoutingAiClient action=llm_provider_missing result=FAILED trace_id={} user_id={} route_key={} provider={} endpoint={} model={}",
            request.traceId(),
            request.userId(),
            request.routeKey(),
            route.provider(),
            route.endpoint(),
            route.model()
        );
        throw new CapturePipelineException(
            CaptureFailureReason.LLM_CALL_FAILED,
            "no ai provider client is available for route_key=" + request.routeKey()
        );
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
