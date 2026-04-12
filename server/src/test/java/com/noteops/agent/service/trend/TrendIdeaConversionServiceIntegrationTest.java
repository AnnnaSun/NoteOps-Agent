package com.noteops.agent.service.trend;

import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-trend-idea-conversion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class TrendIdeaConversionServiceIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrendIdeaConversionService trendIdeaConversionService;

    @Autowired
    private TrendItemRepository trendItemRepository;

    @MockitoBean
    private NoteRepository noteRepository;

    @MockitoBean
    private AgentTraceRepository agentTraceRepository;

    @MockitoBean
    private ToolInvocationLogRepository toolInvocationLogRepository;

    @MockitoBean
    private UserActionEventRepository userActionEventRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists trend_items").update();
        jdbcClient.sql("drop table if exists ideas").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("drop domain if exists timestamptz").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("create domain timestamptz as timestamp with time zone").update();

        jdbcClient.sql("""
            create table ideas (
                id uuid primary key,
                user_id uuid not null,
                source_mode varchar(32) not null,
                source_note_id uuid,
                source_trend_item_id uuid,
                title varchar(255) not null,
                raw_description text,
                status varchar(32) not null,
                assessment_result jsonb not null default '{}',
                created_at timestamptz not null default current_timestamp,
                updated_at timestamptz not null default current_timestamp,
                constraint chk_ideas_source_binding check (
                    (source_mode = 'FROM_NOTE' and source_note_id is not null and source_trend_item_id is null)
                    or (source_mode = 'FROM_TREND' and source_note_id is null and source_trend_item_id is not null)
                    or (source_mode = 'MANUAL' and source_note_id is null and source_trend_item_id is null)
                )
            )
            """).update();
        jdbcClient.sql("""
            create table trend_items (
                id uuid primary key,
                user_id uuid not null,
                source_type varchar(32) not null,
                source_item_key varchar(255) not null,
                title varchar(255) not null,
                url text not null,
                summary text,
                normalized_score numeric(6, 2) not null default 0,
                analysis_payload jsonb not null default '{}',
                extra_attributes jsonb not null default '{}',
                status varchar(32) not null,
                suggested_action varchar(32),
                source_published_at timestamptz,
                last_ingested_at timestamptz not null default current_timestamp,
                converted_note_id uuid,
                converted_idea_id uuid,
                created_at timestamptz not null default current_timestamp,
                updated_at timestamptz not null default current_timestamp,
                constraint uq_trend_items_source_key unique (user_id, source_type, source_item_key)
            )
            """).update();
    }

    @Test
    void promotesTrendToIdeaAndReadsItThroughIdeaController() throws Exception {
        UUID userId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord trendItem = createAnalyzedTrendItem(userId);

        TrendIdeaConversionService.PromoteToIdeaResult result = trendIdeaConversionService.promoteToIdea(
            new TrendIdeaConversionService.PromoteToIdeaCommand(
                trendItem.id(),
                userId,
                UUID.randomUUID(),
                "Promote to idea for assess"
            )
        );

        assertThat(result.idea().sourceMode()).isEqualTo(IdeaSourceMode.FROM_TREND);
        assertThat(result.idea().sourceTrendItemId()).isEqualTo(trendItem.id());
        assertThat(result.idea().status()).isEqualTo(IdeaStatus.CAPTURED);
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.PROMOTED_TO_IDEA);
        assertThat(result.trendItem().convertedIdeaId()).isEqualTo(result.idea().id());

        mockMvc.perform(get("/api/v1/ideas/{id}", result.idea().id())
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(result.idea().id().toString()))
            .andExpect(jsonPath("$.data.source_mode").value("FROM_TREND"))
            .andExpect(jsonPath("$.data.source_trend_item_id").value(trendItem.id().toString()))
            .andExpect(jsonPath("$.data.source_note_id").value(nullValue()))
            .andExpect(jsonPath("$.data.status").value("CAPTURED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    private TrendItemRepository.TrendItemRecord createAnalyzedTrendItem(UUID userId) {
        return trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-trend-idea-1",
            "A trend worth promoting",
            "https://news.ycombinator.com/item?id=123456",
            "Trend summary for idea",
            93.0,
            TrendAnalysisPayload.fromMap(Map.of(
                "summary", "Analysis summary for idea conversion",
                "why_it_matters", "It should become an idea",
                "topic_tags", List.of("ideas", "trend"),
                "signal_type", "DISCUSSION",
                "note_worthy", false,
                "idea_worthy", true,
                "suggested_action", "PROMOTE_TO_IDEA",
                "reasoning_summary", "Strong idea candidate"
            )),
            Map.of(),
            TrendItemStatus.ANALYZED,
            TrendActionType.PROMOTE_TO_IDEA,
            Instant.parse("2026-04-11T08:00:00Z"),
            Instant.parse("2026-04-11T08:05:00Z"),
            null,
            null
        );
    }
}
