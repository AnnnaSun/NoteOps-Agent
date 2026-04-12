package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendSourceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrendSourceRegistry {

    private final Map<TrendSourceType, TrendSourceConnector> connectorsByType;

    public TrendSourceRegistry(List<TrendSourceConnector> connectors) {
        this.connectorsByType = indexConnectors(connectors);
    }

    public TrendSourceConnector getRequired(TrendSourceType sourceType) {
        TrendSourceConnector connector = connectorsByType.get(sourceType);
        if (connector == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "TREND_SOURCE_NOT_REGISTERED",
                "trend source " + sourceType.name() + " is not registered"
            );
        }
        return connector;
    }

    public List<ResolvedTrendSource> resolveAll(List<TrendSourceType> sourceTypes) {
        return sourceTypes.stream()
            .map(this::getRequired)
            .map(connector -> new ResolvedTrendSource(connector.sourceType(), connector.displayName()))
            .toList();
    }

    private Map<TrendSourceType, TrendSourceConnector> indexConnectors(Collection<TrendSourceConnector> connectors) {
        Map<TrendSourceType, TrendSourceConnector> indexed = new LinkedHashMap<>();
        for (TrendSourceConnector connector : connectors) {
            TrendSourceConnector previous = indexed.putIfAbsent(connector.sourceType(), connector);
            if (previous != null) {
                throw new IllegalStateException("Duplicate trend source connector registration for " + connector.sourceType().name());
            }
        }
        return Map.copyOf(indexed);
    }

    public record ResolvedTrendSource(
        TrendSourceType sourceType,
        String displayName
    ) {
    }
}
