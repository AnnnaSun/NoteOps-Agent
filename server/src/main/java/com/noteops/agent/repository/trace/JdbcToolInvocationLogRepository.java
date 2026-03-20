package com.noteops.agent.repository.trace;

import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcToolInvocationLogRepository implements ToolInvocationLogRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcToolInvocationLogRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public void append(UUID userId,
                       UUID traceId,
                       String toolName,
                       String status,
                       Map<String, Object> inputDigest,
                       Map<String, Object> outputDigest,
                       Integer latencyMs,
                       String errorCode,
                       String errorMessage) {
        jdbcClient.sql("""
            insert into tool_invocation_logs (
                id, user_id, trace_id, tool_name, status, input_digest, output_digest, latency_ms, error_code, error_message
            ) values (
                :id, :userId, :traceId, :toolName, :status, cast(:inputDigest as jsonb), cast(:outputDigest as jsonb),
                :latencyMs, :errorCode, :errorMessage
            )
            """)
            .param("id", UUID.randomUUID())
            .param("userId", userId)
            .param("traceId", traceId)
            .param("toolName", toolName)
            .param("status", status)
            .param("inputDigest", jsonSupport.write(inputDigest))
            .param("outputDigest", jsonSupport.write(outputDigest))
            .param("latencyMs", latencyMs)
            .param("errorCode", errorCode)
            .param("errorMessage", errorMessage)
            .update();
    }
}
