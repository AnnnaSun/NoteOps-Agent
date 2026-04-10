package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendSourceRegistryTest {

    @Test
    void resolvesRegisteredSourcesInRequestedOrder() {
        TrendSourceRegistry registry = new TrendSourceRegistry(List.of(
            connector(TrendSourceType.HN, "Hacker News"),
            connector(TrendSourceType.GITHUB, "GitHub")
        ));

        List<TrendSourceRegistry.ResolvedTrendSource> sources = registry.resolveAll(List.of(
            TrendSourceType.HN,
            TrendSourceType.GITHUB
        ));

        assertThat(sources).extracting(TrendSourceRegistry.ResolvedTrendSource::sourceType)
            .containsExactly(TrendSourceType.HN, TrendSourceType.GITHUB);
    }

    @Test
    void rejectsUnknownSourceType() {
        TrendSourceRegistry registry = new TrendSourceRegistry(List.of(
            connector(TrendSourceType.HN, "Hacker News")
        ));

        assertThatThrownBy(() -> registry.getRequired(TrendSourceType.GITHUB))
            .isInstanceOf(ApiException.class)
            .hasMessage("trend source GITHUB is not registered");
    }

    @Test
    void rejectsDuplicateRegistrations() {
        assertThatThrownBy(() -> new TrendSourceRegistry(List.of(
            connector(TrendSourceType.HN, "Hacker News"),
            connector(TrendSourceType.HN, "HN duplicate")
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HN");
    }

    private TrendSourceConnector connector(TrendSourceType sourceType, String displayName) {
        return new TrendSourceConnector() {
            @Override
            public TrendSourceType sourceType() {
                return sourceType;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<FetchedTrendCandidate> fetchCandidates(FetchCommand command) {
                return List.of(
                    new FetchedTrendCandidate(
                        sourceType,
                        UUID.randomUUID().toString(),
                        displayName + " title",
                        "https://example.com/" + sourceType.name().toLowerCase(),
                        50.0,
                        null,
                        java.util.Map.of()
                    )
                );
            }
        };
    }
}
