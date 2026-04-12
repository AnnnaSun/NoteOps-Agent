package com.noteops.agent.service.trend;

import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-trend-analysis-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class TrendAnalysisServiceIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TrendItemRepository trendItemRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists trend_items").update();
        jdbcClient.sql("drop table if exists ideas").update();
        jdbcClient.sql("drop table if exists notes").update();
        jdbcClient.sql("drop domain if exists timestamptz").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("create domain timestamptz as timestamp with time zone").update();

        jdbcClient.sql("create table notes (id uuid primary key)").update();
        jdbcClient.sql("create table ideas (id uuid primary key)").update();
        executeTrendMigration();
    }

    @Test
    void rollsBackAnalysisWriteWhenCompletedToolLogAppendFails() {
        UUID userId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord created = trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-rollback",
            "Rollback candidate",
            "https://news.ycombinator.com/item?id=rollback",
            null,
            92.0,
            TrendAnalysisPayload.empty(),
            Map.of("seed", "rollback"),
            TrendItemStatus.INGESTED,
            null,
            Instant.parse("2026-04-10T10:00:00Z"),
            Instant.parse("2026-04-10T10:05:00Z"),
            null,
            null
        );

        TrendAnalysisService service = new TrendAnalysisService(
            trendItemRepository,
            new FailingCompletedToolInvocationLogRepository(),
            new StubTrendAgent(),
            new DataSourceTransactionManager(dataSource)
        );

        assertThatThrownBy(() -> service.analyzePersistedItems(
            userId,
            UUID.randomUUID(),
            List.of(created)
        )).hasMessage("tool log append failed");

        TrendItemRepository.TrendItemRecord reloaded = trendItemRepository.findByIdAndUserId(created.id(), userId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(TrendItemStatus.INGESTED);
        assertThat(reloaded.summary()).isNull();
        assertThat(reloaded.suggestedAction()).isNull();
        assertThat(reloaded.analysisPayload()).isEqualTo(TrendAnalysisPayload.empty());
    }

    private void executeTrendMigration() {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(
                    new ClassPathResource("db/migration/V4__create_trend_items_table.sql"),
                    StandardCharsets.UTF_8
                )
            );
        } catch (Exception exception) {
            throw new RuntimeException("Failed to execute V4 trend migration in test setup", exception);
        }
    }

    private static final class FailingCompletedToolInvocationLogRepository implements ToolInvocationLogRepository {

        @Override
        public void append(UUID userId,
                           UUID traceId,
                           String toolName,
                           String status,
                           Map<String, Object> inputDigest,
                           Map<String, Object> outputDigest,
                           Integer latencyMs,
                           String errorCode,
                           String errorMessage) {
            if ("COMPLETED".equals(status)) {
                throw new IllegalStateException("tool log append failed");
            }
        }
    }
}
