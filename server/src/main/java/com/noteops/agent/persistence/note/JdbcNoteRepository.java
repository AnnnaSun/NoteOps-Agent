package com.noteops.agent.persistence.note;

import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.domain.note.NoteContentType;
import com.noteops.agent.persistence.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNoteRepository implements NoteRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcNoteRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

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
        UUID noteId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();

        // notes 保存“当前视图”，后续 proposal/治理动作应该围绕这里演进。
        jdbcClient.sql("""
            insert into notes (
                id, user_id, note_type, status, title, current_summary, current_key_points, current_tags,
                current_topic_labels, current_relation_summary, importance_score, latest_content_id, version, extra_attributes
            ) values (
                :id, :userId, :noteType, :status, :title, :currentSummary, cast(:currentKeyPoints as jsonb),
                cast(:currentTags as jsonb), cast(:currentTopicLabels as jsonb), cast(:currentRelationSummary as jsonb),
                :importanceScore, null, :version, cast(:extraAttributes as jsonb)
            )
            """)
            .param("id", noteId)
            .param("userId", userId)
            .param("noteType", "CAPTURE_NOTE")
            .param("status", "ACTIVE")
            .param("title", title)
            .param("currentSummary", currentSummary)
            .param("currentKeyPoints", jsonSupport.write(currentKeyPoints))
            .param("currentTags", jsonSupport.write(List.of()))
            .param("currentTopicLabels", jsonSupport.write(List.of()))
            .param("currentRelationSummary", jsonSupport.write(Map.of()))
            .param("importanceScore", 50)
            .param("version", 1)
            .param("extraAttributes", jsonSupport.write(Map.of()))
            .update();

        // note_contents 保存 capture 原文及清洗结果，保持原始内容追加式落库。
        jdbcClient.sql("""
            insert into note_contents (
                id, user_id, note_id, content_type, source_uri, canonical_uri, source_snapshot, raw_text, clean_text,
                analysis_result, is_current_view_source
            ) values (
                :id, :userId, :noteId, :contentType, :sourceUri, :canonicalUri, cast(:sourceSnapshot as jsonb),
                :rawText, :cleanText, cast(:analysisResult as jsonb), :isCurrentViewSource
            )
            """)
            .param("id", contentId)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("contentType", NoteContentType.CAPTURE_RAW.name())
            .param("sourceUri", sourceUri)
            .param("canonicalUri", sourceUri)
            .param("sourceSnapshot", jsonSupport.write(sourceSnapshot))
            .param("rawText", rawText)
            .param("cleanText", cleanText)
            .param("analysisResult", jsonSupport.write(analysisResult))
            .param("isCurrentViewSource", true)
            .update();

        // latest_content_id 回指当前最新块，方便详情查询直接关联最近一次原始内容。
        jdbcClient.sql("""
            update notes
            set latest_content_id = :contentId,
                updated_at = current_timestamp
            where id = :noteId
            """)
            .param("contentId", contentId)
            .param("noteId", noteId)
            .update();

        return new NoteCreationResult(noteId, contentId);
    }

    @Override
    public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
        return jdbcClient.sql("""
            select n.id,
                   n.user_id,
                   n.title,
                   n.current_summary,
                   n.current_key_points,
                   n.latest_content_id,
                   n.created_at,
                   n.updated_at,
                   nc.content_type as latest_content_type,
                   nc.source_uri,
                   nc.raw_text,
                   nc.clean_text
            from notes n
            left join note_contents nc on nc.id = n.latest_content_id
            where n.id = :noteId and n.user_id = :userId
            """)
            .param("noteId", noteId)
            .param("userId", userId)
            .query((rs, rowNum) -> new NoteQueryService.NoteDetailView(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                rs.getString("current_summary"),
                jsonSupport.readStringList(rs.getString("current_key_points")),
                rs.getObject("latest_content_id", UUID.class),
                rs.getString("latest_content_type"),
                rs.getString("source_uri"),
                rs.getString("raw_text"),
                rs.getString("clean_text"),
                asInstant(rs.getTimestamp("created_at")),
                asInstant(rs.getTimestamp("updated_at"))
            ))
            .optional();
    }

    @Override
    public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
        // M3 的列表查询只返回当前解释层摘要，不把 note_contents 原文整批带出来。
        return jdbcClient.sql("""
            select n.id,
                   n.user_id,
                   n.title,
                   n.current_summary,
                   n.current_key_points,
                   n.latest_content_id,
                   n.updated_at
            from notes n
            where n.user_id = :userId
            order by n.updated_at desc
            """)
            .param("userId", userId)
            .query((rs, rowNum) -> new NoteQueryService.NoteSummaryView(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                rs.getString("current_summary"),
                jsonSupport.readStringList(rs.getString("current_key_points")),
                rs.getObject("latest_content_id", UUID.class),
                asInstant(rs.getTimestamp("updated_at"))
            ))
            .list();
    }

    private Instant asInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
