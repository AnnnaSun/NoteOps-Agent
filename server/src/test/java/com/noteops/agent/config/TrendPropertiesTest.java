package com.noteops.agent.config;

import com.noteops.agent.model.trend.TrendSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-trend-properties;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class TrendPropertiesTest {

    @Autowired
    private TrendProperties trendProperties;

    @Test
    void bindsDefaultPlanFromApplicationConfiguration() {
        TrendProperties.DefaultPlan defaultPlan = trendProperties.defaultPlan();

        assertThat(defaultPlan.planKey()).isEqualTo("default_ai_engineering_trends");
        assertThat(defaultPlan.enabled()).isTrue();
        assertThat(defaultPlan.sources()).containsExactly(TrendSourceType.HN, TrendSourceType.GITHUB);
        assertThat(defaultPlan.fetchLimitPerSource()).isEqualTo(5);
        assertThat(defaultPlan.schedule()).isEqualTo("DAILY");
        assertThat(defaultPlan.keywordBias()).containsExactly(
            "agent",
            "llm",
            "memory",
            "retrieval",
            "tooling",
            "coding"
        );
        assertThat(defaultPlan.autoIngest()).isTrue();
        assertThat(defaultPlan.autoConvert()).isFalse();
    }
}
