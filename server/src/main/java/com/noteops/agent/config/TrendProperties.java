package com.noteops.agent.config;

import com.noteops.agent.model.trend.TrendSourceType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "noteops.trend")
public record TrendProperties(
    DefaultPlan defaultPlan
) {

    public TrendProperties {
        defaultPlan = defaultPlan == null
            ? new DefaultPlan("default_ai_engineering_trends", true, List.of(), 5, "DAILY", List.of(), true, false)
            : defaultPlan;
    }

    public record DefaultPlan(
        String planKey,
        boolean enabled,
        List<TrendSourceType> sources,
        int fetchLimitPerSource,
        String schedule,
        List<String> keywordBias,
        boolean autoIngest,
        boolean autoConvert
    ) {

        public DefaultPlan {
            planKey = blankToDefault(planKey, "default_ai_engineering_trends");
            sources = sources == null ? List.of() : List.copyOf(sources);
            fetchLimitPerSource = fetchLimitPerSource <= 0 ? 5 : fetchLimitPerSource;
            schedule = blankToDefault(schedule, "DAILY");
            keywordBias = keywordBias == null ? List.of() : List.copyOf(keywordBias);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
