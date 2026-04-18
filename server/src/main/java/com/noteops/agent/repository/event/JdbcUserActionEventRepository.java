package com.noteops.agent.repository.event;

import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcUserActionEventRepository implements UserActionEventRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcUserActionEventRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
        jdbcClient.sql("""
            insert into user_action_events (
                id, user_id, event_type, entity_type, entity_id, trace_id, payload
            ) values (
                :id, :userId, :eventType, :entityType, :entityId, :traceId, cast(:payload as jsonb)
            )
            """)
            .param("id", UUID.randomUUID())
            .param("userId", userId)
            .param("eventType", eventType)
            .param("entityType", entityType)
            .param("entityId", entityId)
            .param("traceId", traceId)
            .param("payload", jsonSupport.write(payload))
            .update();
    }

    @Override
    public List<UserActionEventRecord> findRecentByUserId(UUID userId, int limit) {
        int normalizedLimit = Math.max(1, limit);
        return jdbcClient.sql("""
            select id, user_id, event_type, entity_type, entity_id, trace_id, payload, created_at
            from user_action_events
            where user_id = :userId
            order by created_at desc
            limit :limit
            """)
            .param("userId", userId)
            .param("limit", normalizedLimit)
            .query((rs, rowNum) -> new UserActionEventRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getObject("trace_id", UUID.class),
                jsonSupport.readMap(rs.getString("payload")),
                timestampToInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
