package com.noteops.agent.repository.trend;

import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTrendItemRepository implements TrendItemRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcTrendItemRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public TrendItemRecord create(UUID userId,
                                  TrendSourceType sourceType,
                                  String sourceItemKey,
                                  String title,
                                  String url,
                                  String summary,
                                  double normalizedScore,
                                  TrendAnalysisPayload analysisPayload,
                                  Map<String, Object> extraAttributes,
                                  TrendItemStatus status,
                                  TrendActionType suggestedAction,
                                  Instant sourcePublishedAt,
                                  Instant lastIngestedAt,
                                  UUID convertedNoteId,
                                  UUID convertedIdeaId) {
        UUID trendItemId = UUID.randomUUID();
        TrendAnalysisPayload effectivePayload = analysisPayload == null ? TrendAnalysisPayload.empty() : analysisPayload;
        Map<String, Object> effectiveExtraAttributes = extraAttributes == null ? Map.of() : extraAttributes;
        jdbcClient.sql("""
            insert into trend_items (
                id, user_id, source_type, source_item_key, title, url, summary, normalized_score,
                analysis_payload, extra_attributes, status, suggested_action, source_published_at,
                last_ingested_at, converted_note_id, converted_idea_id
            ) values (
                :id, :userId, :sourceType, :sourceItemKey, :title, :url, :summary, :normalizedScore,
                cast(:analysisPayload as jsonb), cast(:extraAttributes as jsonb), :status, :suggestedAction,
                :sourcePublishedAt, :lastIngestedAt, :convertedNoteId, :convertedIdeaId
            )
            """)
            .param("id", trendItemId)
            .param("userId", userId)
            .param("sourceType", sourceType.name())
            .param("sourceItemKey", sourceItemKey)
            .param("title", title)
            .param("url", url)
            .param("summary", summary)
            .param("normalizedScore", normalizedScore)
            .param("analysisPayload", jsonSupport.write(effectivePayload.toMap()))
            .param("extraAttributes", jsonSupport.write(effectiveExtraAttributes))
            .param("status", status.name())
            .param("suggestedAction", suggestedAction == null ? null : suggestedAction.name())
            .param("sourcePublishedAt", toTimestamp(sourcePublishedAt))
            .param("lastIngestedAt", toTimestamp(lastIngestedAt))
            .param("convertedNoteId", convertedNoteId)
            .param("convertedIdeaId", convertedIdeaId)
            .update();
        return findByIdAndUserId(trendItemId, userId).orElseThrow();
    }

    @Override
    public Optional<TrendItemRecord> findByIdAndUserId(UUID trendItemId, UUID userId) {
        return jdbcClient.sql(BASE_SELECT + """
            where id = :trendItemId and user_id = :userId
            """)
            .param("trendItemId", trendItemId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_item_key"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("summary"),
                rs.getDouble("normalized_score"),
                rs.getString("analysis_payload"),
                rs.getString("extra_attributes"),
                rs.getString("status"),
                rs.getString("suggested_action"),
                rs.getTimestamp("source_published_at"),
                rs.getTimestamp("last_ingested_at"),
                rs.getObject("converted_note_id", UUID.class),
                rs.getObject("converted_idea_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
            ))
            .optional();
    }

    @Override
    public Optional<TrendItemRecord> findBySourceKey(UUID userId, TrendSourceType sourceType, String sourceItemKey) {
        return jdbcClient.sql(BASE_SELECT + """
            where user_id = :userId
              and source_type = :sourceType
              and source_item_key = :sourceItemKey
            """)
            .param("userId", userId)
            .param("sourceType", sourceType.name())
            .param("sourceItemKey", sourceItemKey)
            .query((rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_item_key"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("summary"),
                rs.getDouble("normalized_score"),
                rs.getString("analysis_payload"),
                rs.getString("extra_attributes"),
                rs.getString("status"),
                rs.getString("suggested_action"),
                rs.getTimestamp("source_published_at"),
                rs.getTimestamp("last_ingested_at"),
                rs.getObject("converted_note_id", UUID.class),
                rs.getObject("converted_idea_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
            ))
            .optional();
    }

    @Override
    public List<TrendItemRecord> findInboxByUserId(UUID userId, TrendItemStatus status, TrendSourceType sourceType) {
        Objects.requireNonNull(status, "status must not be null");
        StringBuilder sql = new StringBuilder(BASE_SELECT)
            .append("""
                where user_id = :userId
                  and status = :status
                """);
        if (sourceType != null) {
            sql.append("  and source_type = :sourceType\n");
        }
        sql.append("order by updated_at desc");

        var query = jdbcClient.sql(sql.toString())
            .param("userId", userId)
            .param("status", status.name());
        if (sourceType != null) {
            query = query.param("sourceType", sourceType.name());
        }
        return query.query((rs, rowNum) -> mapRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("source_type"),
            rs.getString("source_item_key"),
            rs.getString("title"),
            rs.getString("url"),
            rs.getString("summary"),
            rs.getDouble("normalized_score"),
            rs.getString("analysis_payload"),
            rs.getString("extra_attributes"),
            rs.getString("status"),
            rs.getString("suggested_action"),
            rs.getTimestamp("source_published_at"),
            rs.getTimestamp("last_ingested_at"),
            rs.getObject("converted_note_id", UUID.class),
            rs.getObject("converted_idea_id", UUID.class),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
        )).list();
    }

    @Override
    public TrendItemIngestResult upsertIngested(UUID userId,
                                                TrendSourceType sourceType,
                                                String sourceItemKey,
                                                String title,
                                                String url,
                                                double normalizedScore,
                                                Map<String, Object> extraAttributes,
                                                Instant sourcePublishedAt,
                                                Instant lastIngestedAt) {
        Optional<TrendItemRecord> existing = findBySourceKey(userId, sourceType, sourceItemKey);
        if (existing.isPresent()) {
            return touchExisting(existing.orElseThrow(), sourcePublishedAt, lastIngestedAt);
        }

        try {
            TrendItemRecord created = create(
                userId,
                sourceType,
                sourceItemKey,
                title,
                url,
                null,
                normalizedScore,
                TrendAnalysisPayload.empty(),
                extraAttributes,
                TrendItemStatus.INGESTED,
                null,
                sourcePublishedAt,
                lastIngestedAt,
                null,
                null
            );
            return new TrendItemIngestResult(created, IngestAction.INSERTED);
        } catch (DuplicateKeyException exception) {
            TrendItemRecord deduped = findBySourceKey(userId, sourceType, sourceItemKey)
                .map(item -> touchExisting(item, sourcePublishedAt, lastIngestedAt).trendItem())
                .orElseThrow();
            return new TrendItemIngestResult(deduped, IngestAction.DEDUPED);
        }
    }

    @Override
    public List<TrendItemRecord> findAllByUserId(UUID userId) {
        return jdbcClient.sql(BASE_SELECT + """
            where user_id = :userId
            order by updated_at desc
            """)
            .param("userId", userId)
            .query((rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_item_key"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("summary"),
                rs.getDouble("normalized_score"),
                rs.getString("analysis_payload"),
                rs.getString("extra_attributes"),
                rs.getString("status"),
                rs.getString("suggested_action"),
                rs.getTimestamp("source_published_at"),
                rs.getTimestamp("last_ingested_at"),
                rs.getObject("converted_note_id", UUID.class),
                rs.getObject("converted_idea_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
            ))
            .list();
    }

    @Override
    public TrendItemRecord updateStatus(UUID trendItemId,
                                        UUID userId,
                                        TrendItemStatus status,
                                        TrendActionType suggestedAction,
                                        UUID convertedNoteId,
                                        UUID convertedIdeaId) {
        jdbcClient.sql("""
            update trend_items
            set status = :status,
                suggested_action = :suggestedAction,
                converted_note_id = :convertedNoteId,
                converted_idea_id = :convertedIdeaId,
                updated_at = current_timestamp
            where id = :trendItemId
              and user_id = :userId
            """)
            .param("status", status.name())
            .param("suggestedAction", suggestedAction == null ? null : suggestedAction.name())
            .param("convertedNoteId", convertedNoteId)
            .param("convertedIdeaId", convertedIdeaId)
            .param("trendItemId", trendItemId)
            .param("userId", userId)
            .update();
        return findByIdAndUserId(trendItemId, userId).orElseThrow();
    }

    @Override
    public TrendItemRecord updateAnalysis(UUID trendItemId,
                                          UUID userId,
                                          TrendAnalysisPayload analysisPayload) {
        TrendAnalysisPayload effectivePayload = analysisPayload == null ? TrendAnalysisPayload.empty() : analysisPayload;
        jdbcClient.sql("""
            update trend_items
            set summary = :summary,
                analysis_payload = cast(:analysisPayload as jsonb),
                suggested_action = :suggestedAction,
                status = :status,
                updated_at = current_timestamp
            where id = :trendItemId
              and user_id = :userId
            """)
            .param("summary", effectivePayload.summary())
            .param("analysisPayload", jsonSupport.write(effectivePayload.toMap()))
            .param("suggestedAction", effectivePayload.suggestedAction() == null ? null : effectivePayload.suggestedAction().name())
            .param("status", TrendItemStatus.ANALYZED.name())
            .param("trendItemId", trendItemId)
            .param("userId", userId)
            .update();
        return findByIdAndUserId(trendItemId, userId).orElseThrow();
    }

    private TrendItemIngestResult touchExisting(TrendItemRecord existing,
                                                Instant sourcePublishedAt,
                                                Instant lastIngestedAt) {
        jdbcClient.sql("""
            update trend_items
            set source_published_at = coalesce(source_published_at, :sourcePublishedAt),
                last_ingested_at = :lastIngestedAt,
                updated_at = current_timestamp
            where id = :trendItemId
              and user_id = :userId
            """)
            .param("sourcePublishedAt", toTimestamp(sourcePublishedAt))
            .param("lastIngestedAt", toTimestamp(lastIngestedAt))
            .param("trendItemId", existing.id())
            .param("userId", existing.userId())
            .update();
        return new TrendItemIngestResult(
            findByIdAndUserId(existing.id(), existing.userId()).orElseThrow(),
            IngestAction.DEDUPED
        );
    }

    private TrendItemRecord mapRow(UUID id,
                                   UUID userId,
                                   String sourceType,
                                   String sourceItemKey,
                                   String title,
                                   String url,
                                   String summary,
                                   double normalizedScore,
                                   String analysisPayload,
                                   String extraAttributes,
                                   String status,
                                   String suggestedAction,
                                   Timestamp sourcePublishedAt,
                                   Timestamp lastIngestedAt,
                                   UUID convertedNoteId,
                                   UUID convertedIdeaId,
                                   Timestamp createdAt,
                                   Timestamp updatedAt) {
        return new TrendItemRecord(
            id,
            userId,
            TrendSourceType.valueOf(sourceType),
            sourceItemKey,
            title,
            url,
            summary,
            normalizedScore,
            TrendAnalysisPayload.fromMap(jsonSupport.readMap(analysisPayload)),
            jsonSupport.readMap(extraAttributes),
            TrendItemStatus.valueOf(status),
            suggestedAction == null ? null : TrendActionType.valueOf(suggestedAction),
            timestampToInstant(sourcePublishedAt),
            timestampToInstant(lastIngestedAt),
            convertedNoteId,
            convertedIdeaId,
            timestampToInstant(createdAt),
            timestampToInstant(updatedAt)
        );
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static final String BASE_SELECT = """
        select id, user_id, source_type, source_item_key, title, url, summary, normalized_score,
               analysis_payload, extra_attributes, status, suggested_action, source_published_at,
               last_ingested_at, converted_note_id, converted_idea_id, created_at, updated_at
        from trend_items
        """;
}
