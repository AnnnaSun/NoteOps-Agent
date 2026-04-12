package com.noteops.agent.service.trend;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-trend-note-conversion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class TrendNoteConversionServiceIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrendNoteConversionService trendNoteConversionService;

    @Autowired
    private TrendItemRepository trendItemRepository;

    @Autowired
    private NoteRepository noteRepository;

    @MockitoBean
    private AgentTraceRepository agentTraceRepository;

    @MockitoBean
    private ToolInvocationLogRepository toolInvocationLogRepository;

    @MockitoBean
    private UserActionEventRepository userActionEventRepository;

    @BeforeEach
    void setUpSchema() {
        reset(agentTraceRepository, toolInvocationLogRepository, userActionEventRepository);

        jdbcClient.sql("drop table if exists trend_items").update();
        jdbcClient.sql("drop table if exists note_contents").update();
        jdbcClient.sql("drop table if exists notes").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("drop domain if exists timestamptz").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("create domain timestamptz as timestamp with time zone").update();

        jdbcClient.sql("""
            create table notes (
                id uuid primary key,
                user_id uuid not null,
                note_type varchar(64) not null,
                status varchar(32) not null,
                title varchar(255) not null,
                current_summary text,
                current_key_points jsonb not null default '[]',
                current_tags jsonb not null default '[]',
                current_topic_labels jsonb not null default '[]',
                current_relation_summary jsonb not null default '{}',
                importance_score numeric(5, 2) not null default 0,
                latest_content_id uuid,
                version integer not null default 1,
                extra_attributes jsonb not null default '{}',
                created_at timestamptz not null default current_timestamp,
                updated_at timestamptz not null default current_timestamp
            )
            """).update();
        jdbcClient.sql("""
            create table note_contents (
                id uuid primary key,
                user_id uuid not null,
                note_id uuid not null,
                content_type varchar(32) not null,
                source_uri text,
                canonical_uri text,
                source_snapshot jsonb not null default '{}',
                raw_text text,
                clean_text text,
                analysis_result jsonb not null default '{}',
                is_current_view_source boolean not null default false,
                created_at timestamptz not null default current_timestamp,
                updated_at timestamptz not null default current_timestamp
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
    void savesTrendAsNoteAndReadsItThroughNoteController() throws Exception {
        UUID userId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord trendItem = createAnalyzedTrendItem(userId);

        TrendNoteConversionService.SaveAsNoteResult result = trendNoteConversionService.saveAsNote(
            new TrendNoteConversionService.SaveAsNoteCommand(
                trendItem.id(),
                userId,
                UUID.randomUUID(),
                "Keep the source lineage"
            )
        );

        assertThat(result.noteCreationResult().noteId()).isNotNull();
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.SAVED_AS_NOTE);
        assertThat(result.trendItem().convertedNoteId()).isEqualTo(result.noteCreationResult().noteId());

        TrendItemRepository.TrendItemRecord reloadedTrend = trendItemRepository.findByIdAndUserId(trendItem.id(), userId).orElseThrow();
        assertThat(reloadedTrend.status()).isEqualTo(TrendItemStatus.SAVED_AS_NOTE);
        assertThat(reloadedTrend.convertedNoteId()).isEqualTo(result.noteCreationResult().noteId());
        assertThat(reloadedTrend.suggestedAction()).isEqualTo(TrendActionType.SAVE_AS_NOTE);

        mockMvc.perform(get("/api/v1/notes/{id}", result.noteCreationResult().noteId())
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(result.noteCreationResult().noteId().toString()))
            .andExpect(jsonPath("$.data.title").value(trendItem.title()))
            .andExpect(jsonPath("$.data.current_summary").value("Analysis summary for note conversion"))
            .andExpect(jsonPath("$.data.source_uri").value(trendItem.url()))
            .andExpect(jsonPath("$.data.current_key_points[0]").value("Why this trend matters"))
            .andExpect(jsonPath("$.meta.server_time").exists());

        verify(agentTraceRepository, times(1)).create(
            any(UUID.class),
            any(UUID.class),
            anyString(),
            anyString(),
            anyString(),
            any(UUID.class),
            anyList(),
            anyMap()
        );
        verify(toolInvocationLogRepository, times(2)).append(
            any(UUID.class),
            any(UUID.class),
            anyString(),
            anyString(),
            anyMap(),
            anyMap(),
            any(),
            any(),
            any()
        );
        verify(userActionEventRepository, times(1)).append(
            any(UUID.class),
            anyString(),
            anyString(),
            any(UUID.class),
            any(UUID.class),
            anyMap()
        );
        verify(agentTraceRepository, times(1)).markCompleted(any(UUID.class), anyString(), anyMap());
    }

    @Test
    void rollsBackNoteAndTrendWhenCompletedToolLogWriteFails() {
        UUID userId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord trendItem = createAnalyzedTrendItem(userId);

        doAnswer(invocation -> {
            String status = invocation.getArgument(3, String.class);
            if ("COMPLETED".equals(status)) {
                throw new IllegalStateException("tool log append failed");
            }
            return null;
        }).when(toolInvocationLogRepository).append(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );

        assertThatThrownBy(() -> trendNoteConversionService.saveAsNote(
            new TrendNoteConversionService.SaveAsNoteCommand(
                trendItem.id(),
                userId,
                UUID.randomUUID(),
                null
            )
        )).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tool log append failed");

        TrendItemRepository.TrendItemRecord reloadedTrend = trendItemRepository.findByIdAndUserId(trendItem.id(), userId).orElseThrow();
        assertThat(reloadedTrend.status()).isEqualTo(TrendItemStatus.ANALYZED);
        assertThat(reloadedTrend.convertedNoteId()).isNull();
        assertThat(noteRepository.findAllByUserId(userId)).isEmpty();
    }

    private TrendItemRepository.TrendItemRecord createAnalyzedTrendItem(UUID userId) {
        return trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-note-conversion",
            "Trend title for note",
            "https://news.ycombinator.com/item?id=123456",
            "Trend summary for note",
            91.0,
            TrendAnalysisPayload.fromMap(Map.of(
                "summary", "Analysis summary for note conversion",
                "why_it_matters", "Why this trend matters",
                "topic_tags", List.of("agents", "notes"),
                "signal_type", "DISCUSSION",
                "note_worthy", true,
                "idea_worthy", false,
                "suggested_action", "SAVE_AS_NOTE",
                "reasoning_summary", "Strong note candidate"
            )),
            Map.of(),
            TrendItemStatus.ANALYZED,
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-11T08:00:00Z"),
            Instant.parse("2026-04-11T08:05:00Z"),
            null,
            null
        );
    }

    @TestConfiguration
    static class NoteRepositoryTestConfig {

        @Bean
        @Primary
        NoteRepository noteRepository(com.noteops.agent.repository.note.JdbcNoteRepository jdbcNoteRepository) {
            return new NoteRepository() {
                @Override
                public NoteCreationResult create(UUID userId,
                                                 String title,
                                                 String currentSummary,
                                                 List<String> currentKeyPoints,
                                                 String sourceUri,
                                                 String rawText,
                                                 String cleanText,
                                                 Map<String, Object> sourceSnapshot,
                                                 Map<String, Object> analysisResult) {
                    return jdbcNoteRepository.create(
                        userId,
                        title,
                        currentSummary,
                        currentKeyPoints,
                        sourceUri,
                        rawText,
                        cleanText,
                        sourceSnapshot,
                        analysisResult
                    );
                }

                @Override
                public NoteCreationResult create(UUID userId,
                                                 String title,
                                                 String currentSummary,
                                                 List<String> currentKeyPoints,
                                                 List<String> currentTags,
                                                 String sourceUri,
                                                 String rawText,
                                                 String cleanText,
                                                 Map<String, Object> sourceSnapshot,
                                                 Map<String, Object> analysisResult) {
                    return jdbcNoteRepository.create(
                        userId,
                        title,
                        currentSummary,
                        currentKeyPoints,
                        currentTags,
                        sourceUri,
                        rawText,
                        cleanText,
                        sourceSnapshot,
                        analysisResult
                    );
                }

                @Override
                public java.util.Optional<com.noteops.agent.service.note.NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
                    return jdbcNoteRepository.findByIdAndUserId(noteId, userId);
                }

                @Override
                public List<com.noteops.agent.service.note.NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
                    return jdbcNoteRepository.findAllByUserId(userId);
                }

                @Override
                public List<com.noteops.agent.service.note.NoteQueryService.NoteEvidenceView> findEvidenceByNoteIdAndUserId(UUID noteId, UUID userId) {
                    return List.of();
                }
            };
        }
    }
}
