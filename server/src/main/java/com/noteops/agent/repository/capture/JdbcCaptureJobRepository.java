package com.noteops.agent.repository.capture;

import com.noteops.agent.service.capture.CaptureApplicationService;
import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.model.capture.CaptureJobStatus;
import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCaptureJobRepository implements CaptureJobRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcCaptureJobRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public void createReceived(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput) {
        jdbcClient.sql("""
            insert into capture_jobs (
                id, user_id, input_type, source_uri, raw_input, status, extracted_payload, analysis_result, consolidation_result
            ) values (
                :id, :userId, :inputType, :sourceUri, :rawInput, :status,
                cast(:extractedPayload as jsonb), cast(:analysisResult as jsonb), cast(:consolidationResult as jsonb)
            )
            """)
            .param("id", id)
            .param("userId", userId)
            .param("inputType", inputType.name())
            .param("sourceUri", sourceUri)
            .param("rawInput", rawInput)
            .param("status", CaptureJobStatus.RECEIVED.name())
            .param("extractedPayload", jsonSupport.write(Map.of()))
            .param("analysisResult", jsonSupport.write(Map.of()))
            .param("consolidationResult", jsonSupport.write(Map.of()))
            .update();
    }

    @Override
    public void markExtracting(UUID captureId) {
        // Capture 作业进入抽取阶段，只更新状态，不改写业务内容。
        updateStatus(captureId, CaptureJobStatus.EXTRACTING);
    }

    @Override
    public void saveExtractionResult(UUID captureId, Map<String, Object> extractedPayload) {
        updatePayload(captureId, "extracted_payload", extractedPayload);
    }

    @Override
    public void markAnalyzing(UUID captureId) {
        // Capture 作业进入分析阶段，只推进状态机，不触碰已有 payload。
        updateStatus(captureId, CaptureJobStatus.ANALYZING);
    }

    @Override
    public void saveAnalysisResult(UUID captureId, CaptureAnalysisResult analysisResult) {
        updatePayload(captureId, "analysis_result", analysisResult == null ? Map.of() : analysisResult.toMap());
    }

    @Override
    public void markConsolidating(UUID captureId) {
        // Capture 作业进入落库合并阶段，状态推进由编排层统一控制。
        updateStatus(captureId, CaptureJobStatus.CONSOLIDATING);
    }

    @Override
    public void saveConsolidationResult(UUID captureId, Map<String, Object> consolidationResult) {
        updatePayload(captureId, "consolidation_result", consolidationResult);
    }

    @Override
    public void markCompleted(UUID captureId) {
        // 结束态只更新状态位，保留分析和合并结果供详情页回看。
        updateStatus(captureId, CaptureJobStatus.COMPLETED);
    }

    @Override
    public void markFailed(UUID captureId, CaptureFailureReason failureReason, String errorMessage) {
        jdbcClient.sql("""
            update capture_jobs
            set status = :status,
                error_code = :errorCode,
                error_message = :errorMessage,
                updated_at = current_timestamp
            where id = :captureId
            """)
            .param("status", CaptureJobStatus.FAILED.name())
            .param("errorCode", failureReason.name())
            .param("errorMessage", errorMessage)
            .param("captureId", captureId)
            .update();
    }

    @Override
    public Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, input_type, status, analysis_result, consolidation_result, error_code, error_message, created_at, updated_at
            from capture_jobs
            where id = :captureId and user_id = :userId
            """)
            .param("captureId", captureId)
            .param("userId", userId)
            .query((rs, rowNum) -> {
                // 将关系型字段和 JSON payload 重新组装成对外视图对象。
                Map<String, Object> consolidationResult = jsonSupport.readMap(rs.getString("consolidation_result"));
                CaptureAnalysisResult analysisPreview = CaptureAnalysisResult.fromMap(jsonSupport.readMap(rs.getString("analysis_result")));
                return new CaptureApplicationService.CaptureView(
                    rs.getObject("id", UUID.class),
                    CaptureInputType.valueOf(rs.getString("input_type")),
                    CaptureJobStatus.valueOf(rs.getString("status")),
                    uuidValue(consolidationResult.get("note_id")),
                    failureReason(rs.getString("error_code")),
                    analysisPreview,
                    asInstant(rs.getTimestamp("created_at")),
                    asInstant(rs.getTimestamp("updated_at")),
                    findTraceId(captureId)
                );
            })
            .optional();
    }

    private void updateStatus(UUID captureId, CaptureJobStatus status) {
        // 统一封装 Capture 作业状态迁移，避免各阶段写散。
        jdbcClient.sql("""
            update capture_jobs
            set status = :status,
                updated_at = current_timestamp
            where id = :captureId
            """)
            .param("status", status.name())
            .param("captureId", captureId)
            .update();
    }

    private void updatePayload(UUID captureId, String fieldName, Map<String, Object> payload) {
        // 仅替换单个 JSON payload 字段，保留其他阶段已经写入的数据。
        jdbcClient.sql(
            "update capture_jobs " +
                "set " + fieldName + " = cast(:payload as jsonb), " +
                "updated_at = current_timestamp " +
                "where id = :captureId"
        )
            .param("payload", jsonSupport.write(payload == null ? Map.of() : payload))
            .param("captureId", captureId)
            .update();
    }

    private UUID findTraceId(UUID captureId) {
        return jdbcClient.sql("""
            select id
            from agent_traces
            where entry_type = 'CAPTURE'
              and root_entity_type = 'CAPTURE_JOB'
              and root_entity_id = :captureId
            order by created_at desc
            limit 1
            """)
            .param("captureId", captureId)
            .query(UUID.class)
            .optional()
            .orElse(null);
    }

    private CaptureFailureReason failureReason(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return CaptureFailureReason.valueOf(rawValue);
        } catch (Exception exception) {
            return null;
        }
    }

    private UUID uuidValue(Object value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private Instant asInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
