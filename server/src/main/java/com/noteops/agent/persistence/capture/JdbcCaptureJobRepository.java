package com.noteops.agent.persistence.capture;

import com.noteops.agent.application.capture.CaptureApplicationService;
import com.noteops.agent.domain.capture.CaptureInputType;
import com.noteops.agent.domain.capture.CaptureJobStatus;
import com.noteops.agent.persistence.JsonSupport;
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
    public void updateExtraction(UUID captureId, Map<String, Object> extractedPayload) {
        updateStage(captureId, CaptureJobStatus.EXTRACTING, "extracted_payload", extractedPayload);
    }

    @Override
    public void updateAnalysis(UUID captureId, Map<String, Object> analysisResult) {
        updateStage(captureId, CaptureJobStatus.ANALYZING, "analysis_result", analysisResult);
    }

    @Override
    public void updateConsolidation(UUID captureId, Map<String, Object> consolidationResult) {
        updateStage(captureId, CaptureJobStatus.CONSOLIDATING, "consolidation_result", consolidationResult);
    }

    @Override
    public void markCompleted(UUID captureId) {
        jdbcClient.sql("""
            update capture_jobs
            set status = :status,
                updated_at = current_timestamp
            where id = :captureId
            """)
            .param("status", CaptureJobStatus.COMPLETED.name())
            .param("captureId", captureId)
            .update();
    }

    @Override
    public void markFailed(UUID captureId, String errorCode, String errorMessage) {
        jdbcClient.sql("""
            update capture_jobs
            set status = :status,
                error_code = :errorCode,
                error_message = :errorMessage,
                updated_at = current_timestamp
            where id = :captureId
            """)
            .param("status", CaptureJobStatus.FAILED.name())
            .param("errorCode", errorCode)
            .param("errorMessage", errorMessage)
            .param("captureId", captureId)
            .update();
    }

    @Override
    public Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, input_type, source_uri, raw_input, status, extracted_payload, analysis_result,
                   consolidation_result, error_code, error_message, created_at, updated_at
            from capture_jobs
            where id = :captureId and user_id = :userId
            """)
            .param("captureId", captureId)
            .param("userId", userId)
            .query((rs, rowNum) -> {
                Map<String, Object> consolidationResult = jsonSupport.readMap(rs.getString("consolidation_result"));
                return new CaptureApplicationService.CaptureView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    CaptureInputType.valueOf(rs.getString("input_type")),
                    rs.getString("source_uri"),
                    rs.getString("raw_input"),
                    CaptureJobStatus.valueOf(rs.getString("status")),
                    rs.getString("error_code"),
                    rs.getString("error_message"),
                    asInstant(rs.getTimestamp("created_at")),
                    asInstant(rs.getTimestamp("updated_at")),
                    stringValue(consolidationResult.get("note_id")),
                    stringValue(consolidationResult.get("trace_id"))
                );
            })
            .optional();
    }

    private void updateStage(UUID captureId, CaptureJobStatus status, String fieldName, Map<String, Object> payload) {
        // fieldName 只来自仓储内部固定字段，避免为每个阶段重复写一套 update SQL。
        jdbcClient.sql(
            "update capture_jobs " +
                "set status = :status, " +
                fieldName + " = cast(:payload as jsonb), " +
                "updated_at = current_timestamp " +
                "where id = :captureId"
        )
            .param("status", status.name())
            .param("payload", jsonSupport.write(payload))
            .param("captureId", captureId)
            .update();
    }

    private Instant asInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
