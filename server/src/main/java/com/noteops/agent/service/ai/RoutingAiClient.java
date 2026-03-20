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
    // 按路由优先级逐个尝试 provider，遇到可恢复失败时切换下一个候选。
    public AiResponse analyze(AiRequest request) {
        List<AiProperties.ResolvedRoute> routes = properties.resolveRoutes(
            request.routeKey(),
            request.providerOverride(),
            request.modelOverride()
        );
        CapturePipelineException lastFailure = null;
        for (AiProperties.ResolvedRoute route : routes) {
            AiProviderClient providerClient = providerClients.get(route.provider());
            if (providerClient == null) {
                log.warn(
                    "module=RoutingAiClient action=llm_provider_missing result=SKIPPED trace_id={} user_id={} route_key={} provider={} model={}",
                    request.traceId(),
                    request.userId(),
                    request.routeKey(),
                    route.provider(),
                    route.model()
                );
                continue;
            }
            try {
                if (lastFailure != null) {
                    log.info(
                        "module=RoutingAiClient action=llm_provider_fallback result=RETRYING trace_id={} user_id={} route_key={} provider={} model={}",
                        request.traceId(),
                        request.userId(),
                        request.routeKey(),
                        route.provider(),
                        route.model()
                    );
                }
                return providerClient.analyze(request, route);
            } catch (CapturePipelineException exception) {
                if (!isFallbackable(exception)) {
                    throw exception;
                }
                lastFailure = exception;
                log.warn(
                    "module=RoutingAiClient action=llm_provider_fallback result=FAILED trace_id={} user_id={} route_key={} provider={} model={} error_code={} error_message={}",
                    request.traceId(),
                    request.userId(),
                    request.routeKey(),
                    route.provider(),
                    route.model(),
                    exception.failureReason().name(),
                    exception.getMessage()
                );
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new CapturePipelineException(
            CaptureFailureReason.LLM_CALL_FAILED,
            "no ai provider candidate is available for route_key=" + request.routeKey()
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

    private boolean isFallbackable(CapturePipelineException exception) {
        return switch (exception.failureReason()) {
            case LLM_CALL_FAILED, LLM_OUTPUT_INVALID -> true;
            default -> false;
        };
    }
}
