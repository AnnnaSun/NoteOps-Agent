package com.noteops.agent.repository.idea;

import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
                             UUID sourceTrendItemId,
                             String title,
                             String rawDescription,
                             IdeaStatus status,
                             IdeaAssessmentResult assessmentResult) {
        requireNonNull(userId, "userId");
        requireNonNull(sourceMode, "sourceMode");
        requireNonNull(status, "status");
        requireNonNull(title, "title");
        UUID ideaId = UUID.randomUUID();
        IdeaAssessmentResult effectiveAssessment = assessmentResult == null ? IdeaAssessmentResult.empty() : assessmentResult;
        jdbcClient.sql("""
            insert into ideas (
                id, user_id, source_mode, source_note_id, source_trend_item_id, title, raw_description, status, assessment_result
            ) values (
                :id, :userId, :sourceMode, :sourceNoteId, :sourceTrendItemId, :title, :rawDescription, :status, cast(:assessmentResult as jsonb)
            )
            """)
            .param("id", ideaId)
            .param("userId", userId)
            .param("sourceMode", sourceMode.name())
            .param("sourceNoteId", sourceNoteId)
            .param("sourceTrendItemId", sourceTrendItemId)
            .param("title", title)
            .param("rawDescription", rawDescription)
            .param("status", status.name())
            .param("assessmentResult", jsonSupport.write(effectiveAssessment.toMap()))
            .update();
        return findByIdAndUserId(ideaId, userId)
            .orElseThrow(() -> new IllegalStateException("created idea could not be reloaded: idea_id=" + ideaId + " user_id=" + userId));
    }

    @Override
    public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, source_mode, source_note_id, source_trend_item_id, title, raw_description, status,
                   assessment_result, created_at, updated_at
            from ideas
            where id = :ideaId and user_id = :userId
            """)
            .param("ideaId", ideaId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapIdeaRecord(rs))
            .optional();
    }

    @Override
    public List<IdeaRecord> findAllByUserId(UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, source_mode, source_note_id, source_trend_item_id, title, raw_description, status,
                   assessment_result, created_at, updated_at
            from ideas
            where user_id = :userId
            order by updated_at desc
            """)
            .param("userId", userId)
            .query((rs, rowNum) -> mapIdeaRecord(rs))
            .list();
    }

    @Override
    // 更新 assessment 结果并推进状态，供 assess 主链路持久化使用。
    public IdeaRecord updateAssessment(UUID ideaId,
                                       UUID userId,
                                       IdeaAssessmentResult assessmentResult,
                                       IdeaStatus status) {
        IdeaAssessmentResult effectiveAssessment = assessmentResult == null ? IdeaAssessmentResult.empty() : assessmentResult;
        jdbcClient.sql("""
            update ideas
            set assessment_result = cast(:assessmentResult as jsonb),
                status = :status,
                updated_at = current_timestamp
            where id = :ideaId and user_id = :userId
            """)
            .param("assessmentResult", jsonSupport.write(effectiveAssessment.toMap()))
            .param("status", status.name())
            .param("ideaId", ideaId)
            .param("userId", userId)
            .update();
        return findByIdAndUserId(ideaId, userId).orElseThrow();
    }

    @Override
    // 单独推进 Idea 状态，供 task 派生等非 assessment 场景复用。
    public IdeaRecord updateStatus(UUID ideaId, UUID userId, IdeaStatus status) {
        jdbcClient.sql("""
            update ideas
            set status = :status,
                updated_at = current_timestamp
            where id = :ideaId and user_id = :userId
            """)
            .param("status", status.name())
            .param("ideaId", ideaId)
            .param("userId", userId)
            .update();
        return findByIdAndUserId(ideaId, userId).orElseThrow();
    }

    @Override
    // 以 compare-and-set 方式推进状态，避免并发请求重复执行同一业务动作。
    public Optional<IdeaRecord> updateStatusIfCurrent(UUID ideaId,
                                                      UUID userId,
                                                      IdeaStatus currentStatus,
                                                      IdeaStatus targetStatus) {
        int updatedRows = jdbcClient.sql("""
            update ideas
            set status = :targetStatus,
                updated_at = current_timestamp
            where id = :ideaId
              and user_id = :userId
              and status = :currentStatus
            """)
            .param("targetStatus", targetStatus.name())
            .param("ideaId", ideaId)
            .param("userId", userId)
            .param("currentStatus", currentStatus.name())
            .update();
        if (updatedRows == 0) {
            return Optional.empty();
        }
        return findByIdAndUserId(ideaId, userId);
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return value;
    }

    private IdeaRecord mapIdeaRecord(java.sql.ResultSet rs) {
        try {
            return new IdeaRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                IdeaSourceMode.valueOf(rs.getString("source_mode")),
                rs.getObject("source_note_id", UUID.class),
                rs.getObject("source_trend_item_id", UUID.class),
                rs.getString("title"),
                rs.getString("raw_description"),
                IdeaStatus.valueOf(rs.getString("status")),
                IdeaAssessmentResult.fromMap(jsonSupport.readMap(rs.getString("assessment_result"))),
                timestampToInstant(rs.getTimestamp("created_at")),
                timestampToInstant(rs.getTimestamp("updated_at"))
            );
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("failed to map idea record", exception);
        }
    }
}
