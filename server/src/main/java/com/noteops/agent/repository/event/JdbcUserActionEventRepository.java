package com.noteops.agent.repository.event;

import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
}
