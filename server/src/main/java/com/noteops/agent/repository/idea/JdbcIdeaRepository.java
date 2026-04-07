package com.noteops.agent.repository.idea;

import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdeaRepository implements IdeaRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcIdeaRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    // 创建 Idea 基线记录，只负责持久化冻结字段，不承担状态推进或治理编排。
    public IdeaRecord create(UUID userId,
                             IdeaSourceMode sourceMode,
                             UUID sourceNoteId,
                             String title,
                             String rawDescription,
                             IdeaStatus status,
                             IdeaAssessmentResult assessmentResult) {
        UUID ideaId = UUID.randomUUID();
        IdeaAssessmentResult effectiveAssessment = assessmentResult == null ? IdeaAssessmentResult.empty() : assessmentResult;
        jdbcClient.sql("""
            insert into ideas (
                id, user_id, source_mode, source_note_id, title, raw_description, status, assessment_result
            ) values (
                :id, :userId, :sourceMode, :sourceNoteId, :title, :rawDescription, :status, cast(:assessmentResult as jsonb)
            )
            """)
            .param("id", ideaId)
            .param("userId", userId)
            .param("sourceMode", sourceMode.name())
            .param("sourceNoteId", sourceNoteId)
            .param("title", title)
            .param("rawDescription", rawDescription)
            .param("status", status.name())
            .param("assessmentResult", jsonSupport.write(effectiveAssessment.toMap()))
            .update();
        return findByIdAndUserId(ideaId, userId).orElseThrow();
    }

    @Override
    public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, source_mode, source_note_id, title, raw_description, status,
                   assessment_result, created_at, updated_at
            from ideas
            where id = :ideaId and user_id = :userId
            """)
            .param("ideaId", ideaId)
            .param("userId", userId)
            .query((rs, rowNum) -> new IdeaRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                IdeaSourceMode.valueOf(rs.getString("source_mode")),
                rs.getObject("source_note_id", UUID.class),
                rs.getString("title"),
                rs.getString("raw_description"),
                IdeaStatus.valueOf(rs.getString("status")),
                IdeaAssessmentResult.fromMap(jsonSupport.readMap(rs.getString("assessment_result"))),
                timestampToInstant(rs.getTimestamp("created_at")),
                timestampToInstant(rs.getTimestamp("updated_at"))
            ))
            .optional();
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
