package com.noteops.agent.repository.trend;

import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-trend-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcTrendItemRepositoryIntegrationTest {

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

        jdbcClient.sql("""
            create table notes (
                id uuid primary key
            )
            """).update();

        jdbcClient.sql("""
            create table ideas (
                id uuid primary key
            )
            """).update();

        executeTrendMigration();
    }

    @Test
    void createsAndLoadsTrendItemWithStructuredPayload() {
        UUID userId = UUID.randomUUID();
        TrendAnalysisPayload analysisPayload = new TrendAnalysisPayload(
            "Compact summary",
            "Why this trend matters",
            List.of("agents", "tooling"),
            "PROJECT",
            true,
            false,
            TrendActionType.SAVE_AS_NOTE,
            "Useful for later note capture"
        );

        TrendItemRepository.TrendItemRecord created = trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-123",
            "Agents are getting better",
            "https://news.ycombinator.com/item?id=123",
            "Compact summary",
            87.25,
            analysisPayload,
            Map.of("rank", 1, "source_snapshot", Map.of("points", 120)),
            TrendItemStatus.INGESTED,
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-10T01:00:00Z"),
            Instant.parse("2026-04-10T02:00:00Z"),
            null,
            null
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.sourceType()).isEqualTo(TrendSourceType.HN);
        assertThat(created.sourceItemKey()).isEqualTo("hn-123");
        assertThat(created.title()).isEqualTo("Agents are getting better");
        assertThat(created.url()).isEqualTo("https://news.ycombinator.com/item?id=123");
        assertThat(created.analysisPayload()).isEqualTo(analysisPayload);
        assertThat(created.extraAttributes()).containsEntry("rank", 1);
        assertThat(created.status()).isEqualTo(TrendItemStatus.INGESTED);
        assertThat(created.suggestedAction()).isEqualTo(TrendActionType.SAVE_AS_NOTE);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        TrendItemRepository.TrendItemRecord loaded = trendItemRepository
            .findByIdAndUserId(created.id(), userId)
            .orElseThrow();

        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void findsBySourceKeyAndEnforcesUserScopedDeduplication() {
        UUID userId = UUID.randomUUID();

        TrendItemRepository.TrendItemRecord created = trendItemRepository.create(
            userId,
            TrendSourceType.GITHUB,
            "repo-openai-codex",
            "Codex repo is trending",
            "https://github.com/openai/codex",
            null,
            65.0,
            TrendAnalysisPayload.empty(),
            Map.of(),
            TrendItemStatus.INGESTED,
            null,
            null,
            Instant.parse("2026-04-10T03:00:00Z"),
            null,
            null
        );

        TrendItemRepository.TrendItemRecord found = trendItemRepository
            .findBySourceKey(userId, TrendSourceType.GITHUB, "repo-openai-codex")
            .orElseThrow();

        assertThat(found.id()).isEqualTo(created.id());

        assertThatThrownBy(() -> trendItemRepository.create(
            userId,
            TrendSourceType.GITHUB,
            "repo-openai-codex",
            "Duplicate key",
            "https://github.com/openai/codex",
            null,
            30.0,
            TrendAnalysisPayload.empty(),
            Map.of(),
            TrendItemStatus.INGESTED,
            null,
            null,
            Instant.parse("2026-04-10T03:30:00Z"),
            null,
            null
        )).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void listsTrendItemsByUserOrderedByUpdatedAtDesc() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID olderId = UUID.randomUUID();

        insertTrendItem(olderId, userId, "older-key", "Older item", 10.0, "INGESTED", "2026-04-10T05:00:00Z");
        UUID newerId = UUID.randomUUID();
        insertTrendItem(newerId, userId, "newer-key", "Newer item", 90.0, "ANALYZED", "2026-04-10T06:00:00Z");
        insertTrendItem(UUID.randomUUID(), otherUserId, "other-key", "Other user item", 99.0, "ANALYZED", "2026-04-10T07:00:00Z");

        List<TrendItemRepository.TrendItemRecord> items = trendItemRepository.findAllByUserId(userId);

        assertThat(items).extracting(TrendItemRepository.TrendItemRecord::id)
            .containsExactly(newerId, olderId);
        assertThat(items).extracting(TrendItemRepository.TrendItemRecord::title)
            .containsExactly("Newer item", "Older item");
    }

    @Test
    void listsInboxItemsByDefaultStatusOrderedByUpdatedAtDesc() {
        UUID userId = UUID.randomUUID();
        UUID olderAnalyzedId = UUID.randomUUID();
        UUID newerAnalyzedId = UUID.randomUUID();
        UUID ignoredId = UUID.randomUUID();

        insertTrendItem(olderAnalyzedId, userId, "older-analyzed", "Older analyzed item", 12.0, "ANALYZED", "2026-04-10T05:00:00Z", "HN");
        insertTrendItem(newerAnalyzedId, userId, "newer-analyzed", "Newer analyzed item", 82.0, "ANALYZED", "2026-04-10T06:00:00Z", "GITHUB");
        insertTrendItem(ignoredId, userId, "ignored-item", "Ignored item", 99.0, "IGNORED", "2026-04-10T07:00:00Z", "HN");

        List<TrendItemRepository.TrendItemRecord> items = trendItemRepository.findInboxByUserId(
            userId,
            TrendItemStatus.ANALYZED,
            null
        );

        assertThat(items).extracting(TrendItemRepository.TrendItemRecord::id)
            .containsExactly(newerAnalyzedId, olderAnalyzedId);
        assertThat(items).extracting(TrendItemRepository.TrendItemRecord::status)
            .containsExactly(TrendItemStatus.ANALYZED, TrendItemStatus.ANALYZED);
    }

    @Test
    void listsInboxItemsByStatusAndSourceType() {
        UUID userId = UUID.randomUUID();
        UUID analyzedGithubId = UUID.randomUUID();
        UUID analyzedHnId = UUID.randomUUID();

        insertTrendItem(analyzedGithubId, userId, "github-analyzed", "GitHub analyzed item", 70.0, "ANALYZED", "2026-04-10T06:30:00Z", "GITHUB");
        insertTrendItem(analyzedHnId, userId, "hn-analyzed", "HN analyzed item", 80.0, "ANALYZED", "2026-04-10T06:45:00Z", "HN");

        List<TrendItemRepository.TrendItemRecord> items = trendItemRepository.findInboxByUserId(
            userId,
            TrendItemStatus.ANALYZED,
            TrendSourceType.GITHUB
        );

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(analyzedGithubId);
        assertThat(items.get(0).sourceType()).isEqualTo(TrendSourceType.GITHUB);
    }

    @Test
    void updatesStatusWithoutApplyingConversionLogic() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        jdbcClient.sql("insert into notes (id) values (:id)").param("id", noteId).update();

        TrendItemRepository.TrendItemRecord created = trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-456",
            "Save as note candidate",
            "https://news.ycombinator.com/item?id=456",
            "Initial summary",
            77.0,
            TrendAnalysisPayload.empty(),
            Map.of("seed", "initial"),
            TrendItemStatus.INGESTED,
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-10T01:00:00Z"),
            Instant.parse("2026-04-10T02:00:00Z"),
            null,
            null
        );

        TrendItemRepository.TrendItemRecord updated = trendItemRepository.updateStatus(
            created.id(),
            userId,
            TrendItemStatus.SAVED_AS_NOTE,
            TrendActionType.SAVE_AS_NOTE,
            noteId,
            null
        );

        assertThat(updated.status()).isEqualTo(TrendItemStatus.SAVED_AS_NOTE);
        assertThat(updated.suggestedAction()).isEqualTo(TrendActionType.SAVE_AS_NOTE);
        assertThat(updated.convertedNoteId()).isEqualTo(noteId);
        assertThat(updated.convertedIdeaId()).isNull();
    }

    @Test
    void upsertsIngestedTrendItemsWithoutCreatingDuplicates() {
        UUID userId = UUID.randomUUID();
        Instant initialIngestedAt = Instant.parse("2026-04-10T08:00:00Z");
        Instant nextIngestedAt = Instant.parse("2026-04-10T09:00:00Z");

        TrendItemRepository.TrendItemIngestResult inserted = trendItemRepository.upsertIngested(
            userId,
            TrendSourceType.GITHUB,
            "repo-openai-codex",
            "openai/codex",
            "https://github.com/openai/codex",
            74.5,
            Map.of("stars", 2500),
            Instant.parse("2026-04-10T07:30:00Z"),
            initialIngestedAt
        );

        TrendItemRepository.TrendItemIngestResult deduped = trendItemRepository.upsertIngested(
            userId,
            TrendSourceType.GITHUB,
            "repo-openai-codex",
            "openai/codex",
            "https://github.com/openai/codex",
            80.0,
            Map.of("stars", 2600),
            Instant.parse("2026-04-10T07:45:00Z"),
            nextIngestedAt
        );

        assertThat(inserted.action()).isEqualTo(TrendItemRepository.IngestAction.INSERTED);
        assertThat(deduped.action()).isEqualTo(TrendItemRepository.IngestAction.DEDUPED);
        assertThat(deduped.trendItem().id()).isEqualTo(inserted.trendItem().id());
        assertThat(deduped.trendItem().lastIngestedAt()).isEqualTo(nextIngestedAt);
        assertThat(deduped.trendItem().normalizedScore()).isEqualTo(74.5);
        assertThat(deduped.trendItem().extraAttributes()).containsEntry("stars", 2500);
        assertThat(deduped.trendItem().title()).isEqualTo("openai/codex");
        assertThat(deduped.trendItem().url()).isEqualTo("https://github.com/openai/codex");

        Integer count = jdbcClient.sql("""
            select count(*) from trend_items
            where user_id = :userId
              and source_type = 'GITHUB'
              and source_item_key = 'repo-openai-codex'
            """)
            .param("userId", userId)
            .query(Integer.class)
            .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void updatesAnalysisPayloadSummaryAndSuggestedAction() {
        UUID userId = UUID.randomUUID();

        TrendItemRepository.TrendItemRecord created = trendItemRepository.create(
            userId,
            TrendSourceType.HN,
            "hn-789",
            "Trend analysis candidate",
            "https://news.ycombinator.com/item?id=789",
            null,
            88.8,
            TrendAnalysisPayload.empty(),
            Map.of("seed", "hn-789"),
            TrendItemStatus.INGESTED,
            null,
            Instant.parse("2026-04-10T10:00:00Z"),
            Instant.parse("2026-04-10T10:05:00Z"),
            null,
            null
        );

        TrendAnalysisPayload analysisPayload = new TrendAnalysisPayload(
            "Compact trend summary",
            "This points to durable agent workflow demand",
            List.of("agents", "workflow"),
            "DISCUSSION",
            true,
            false,
            TrendActionType.SAVE_AS_NOTE,
            "High score and explanatory discussion make this a note candidate"
        );

        TrendItemRepository.TrendItemRecord updated = trendItemRepository.updateAnalysis(
            created.id(),
            userId,
            analysisPayload
        );

        assertThat(updated.status()).isEqualTo(TrendItemStatus.ANALYZED);
        assertThat(updated.summary()).isEqualTo("Compact trend summary");
        assertThat(updated.analysisPayload()).isEqualTo(analysisPayload);
        assertThat(updated.suggestedAction()).isEqualTo(TrendActionType.SAVE_AS_NOTE);
    }

    private void insertTrendItem(UUID id,
                                 UUID userId,
                                 String sourceItemKey,
                                 String title,
                                 double normalizedScore,
                                 String status,
                                 String updatedAt) {
        insertTrendItem(id, userId, sourceItemKey, title, normalizedScore, status, updatedAt, "HN");
    }

    private void insertTrendItem(UUID id,
                                 UUID userId,
                                 String sourceItemKey,
                                 String title,
                                 double normalizedScore,
                                 String status,
                                 String updatedAt,
                                 String sourceType) {
        jdbcClient.sql("""
            insert into trend_items (
                id, user_id, source_type, source_item_key, title, url, summary, normalized_score,
                analysis_payload, extra_attributes, status, suggested_action, source_published_at,
                last_ingested_at, converted_note_id, converted_idea_id, created_at, updated_at
            ) values (
                :id, :userId, :sourceType, :sourceItemKey, :title, :url, null, :normalizedScore,
                cast(:analysisPayload as jsonb), cast(:extraAttributes as jsonb), :status, null, null, null,
                null, null, :createdAt, :updatedAt
            )
            """)
            .param("id", id)
            .param("userId", userId)
            .param("sourceType", sourceType)
            .param("sourceItemKey", sourceItemKey)
            .param("title", title)
            .param("url", "https://example.com/" + sourceItemKey)
            .param("normalizedScore", normalizedScore)
            .param("analysisPayload", "{}")
            .param("extraAttributes", "{}")
            .param("status", status)
            .param("createdAt", Timestamp.from(Instant.parse(updatedAt)))
            .param("updatedAt", Timestamp.from(Instant.parse(updatedAt)))
            .update();
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
}
