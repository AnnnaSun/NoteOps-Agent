package com.noteops.agent.persistence.trace;

import com.noteops.agent.persistence.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcAgentTraceRepository implements AgentTraceRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcAgentTraceRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public UUID create(UUID userId, String goal, UUID captureId, List<String> workerSequence) {
        UUID traceId = UUID.randomUUID();
        jdbcClient.sql("""
            insert into agent_traces (
                id, user_id, entry_type, goal, root_entity_type, root_entity_id, status, orchestrator_state,
                worker_sequence, started_at
            ) values (
                :id, :userId, :entryType, :goal, :rootEntityType, :rootEntityId, :status,
                cast(:orchestratorState as jsonb), cast(:workerSequence as jsonb), current_timestamp
            )
            """)
            .param("id", traceId)
            .param("userId", userId)
            .param("entryType", "CAPTURE")
            .param("goal", goal)
            .param("rootEntityType", "CAPTURE_JOB")
            .param("rootEntityId", captureId)
            .param("status", "RUNNING")
            .param("orchestratorState", jsonSupport.write(Map.of("capture_job_id", captureId)))
            .param("workerSequence", jsonSupport.write(workerSequence))
            .update();
        return traceId;
    }

    @Override
    public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        updateStatus(traceId, "COMPLETED", resultSummary, orchestratorState);
    }

    @Override
    public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        updateStatus(traceId, "FAILED", resultSummary, orchestratorState);
    }

    private void updateStatus(UUID traceId, String status, String resultSummary, Map<String, Object> orchestratorState) {
        jdbcClient.sql("""
            update agent_traces
            set status = :status,
                result_summary = :resultSummary,
                orchestrator_state = cast(:orchestratorState as jsonb),
                ended_at = current_timestamp
            where id = :traceId
            """)
            .param("status", status)
            .param("resultSummary", resultSummary)
            .param("orchestratorState", jsonSupport.write(orchestratorState))
            .param("traceId", traceId)
            .update();
    }
}
