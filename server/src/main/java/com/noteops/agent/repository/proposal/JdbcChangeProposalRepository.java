package com.noteops.agent.repository.proposal;

import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcChangeProposalRepository implements ChangeProposalRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcChangeProposalRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public ChangeProposalApplicationService.ChangeProposalView create(UUID userId,
                                                                      UUID noteId,
                                                                      UUID traceId,
                                                                      String proposalType,
                                                                      ChangeProposalTargetLayer targetLayer,
                                                                      ChangeProposalRiskLevel riskLevel,
                                                                      String diffSummary,
                                                                      Map<String, Object> beforeSnapshot,
                                                                      Map<String, Object> afterSnapshot,
                                                                      List<Map<String, Object>> sourceRefs) {
        UUID proposalId = UUID.randomUUID();
        // proposal 的快照和来源引用都以 JSON 形式保存，便于后续回滚和审计。
        jdbcClient.sql("""
            insert into change_proposals (
                id, user_id, note_id, trace_id, proposal_type, target_layer, risk_level, diff_summary,
                before_snapshot, after_snapshot, source_refs
            ) values (
                :id, :userId, :noteId, :traceId, :proposalType, :targetLayer, :riskLevel, :diffSummary,
                cast(:beforeSnapshot as jsonb), cast(:afterSnapshot as jsonb), cast(:sourceRefs as jsonb)
            )
            """)
            .param("id", proposalId)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("traceId", traceId)
            .param("proposalType", proposalType)
            .param("targetLayer", targetLayer.name())
            .param("riskLevel", riskLevel.name())
            .param("diffSummary", diffSummary)
            .param("beforeSnapshot", jsonSupport.write(beforeSnapshot))
            .param("afterSnapshot", jsonSupport.write(afterSnapshot))
            .param("sourceRefs", jsonSupport.write(sourceRefs))
            .update();
        return findByIdAndUserId(proposalId, userId).orElseThrow();
    }

    @Override
    public List<ChangeProposalApplicationService.ChangeProposalView> findByNoteIdAndUserId(UUID noteId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, trace_id, proposal_type, target_layer, risk_level, diff_summary,
                   before_snapshot, after_snapshot, source_refs, rollback_token, status, created_at, updated_at
            from change_proposals
            where note_id = :noteId and user_id = :userId
            order by created_at desc
            """)
            .param("noteId", noteId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapView(rs))
            .list();
    }

    @Override
    public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndUserId(UUID proposalId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, trace_id, proposal_type, target_layer, risk_level, diff_summary,
                   before_snapshot, after_snapshot, source_refs, rollback_token, status, created_at, updated_at
            from change_proposals
            where id = :proposalId and user_id = :userId
            """)
            .param("proposalId", proposalId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndNoteIdAndUserId(UUID proposalId,
                                                                                                     UUID noteId,
                                                                                                     UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, trace_id, proposal_type, target_layer, risk_level, diff_summary,
                   before_snapshot, after_snapshot, source_refs, rollback_token, status, created_at, updated_at
            from change_proposals
            where id = :proposalId and note_id = :noteId and user_id = :userId
            """)
            .param("proposalId", proposalId)
            .param("noteId", noteId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public void updateStatus(UUID proposalId, ChangeProposalStatus status, String rollbackToken) {
        // 状态迁移时顺手写入 rollback_token，保证已应用 proposal 可逆。
        jdbcClient.sql("""
            update change_proposals
            set status = :status,
                rollback_token = :rollbackToken,
                updated_at = current_timestamp
            where id = :proposalId
            """)
            .param("status", status.name())
            .param("rollbackToken", rollbackToken)
            .param("proposalId", proposalId)
            .update();
    }

    private ChangeProposalApplicationService.ChangeProposalView mapView(java.sql.ResultSet rs) throws java.sql.SQLException {
        // 将 proposal 的关系字段、枚举字段和 JSON 快照还原成视图对象。
        return new ChangeProposalApplicationService.ChangeProposalView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("note_id", UUID.class),
            rs.getObject("trace_id", UUID.class),
            rs.getString("proposal_type"),
            ChangeProposalTargetLayer.valueOf(rs.getString("target_layer")),
            ChangeProposalRiskLevel.valueOf(rs.getString("risk_level")),
            rs.getString("diff_summary"),
            jsonSupport.readMap(rs.getString("before_snapshot")),
            jsonSupport.readMap(rs.getString("after_snapshot")),
            jsonSupport.readMapList(rs.getString("source_refs")),
            rs.getString("rollback_token"),
            ChangeProposalStatus.valueOf(rs.getString("status")),
            timestampToInstant(rs.getTimestamp("created_at")),
            timestampToInstant(rs.getTimestamp("updated_at"))
        );
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
