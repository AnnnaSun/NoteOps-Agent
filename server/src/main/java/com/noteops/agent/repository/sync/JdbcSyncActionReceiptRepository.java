package com.noteops.agent.repository.sync;

import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcSyncActionReceiptRepository implements SyncActionReceiptRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcSyncActionReceiptRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public java.util.Optional<SyncActionReceiptRecord> findByIdempotencyKey(UUID userId, String clientId, String offlineActionId) {
        return jdbcClient.sql("""
            select id, user_id, client_id, offline_action_id, action_type, entity_type, entity_id, trace_id,
                   status, error_code, error_message, payload, occurred_at, processed_at
            from sync_action_receipts
            where user_id = :userId
              and client_id = :clientId
              and offline_action_id = :offlineActionId
            """)
            .param("userId", userId)
            .param("clientId", clientId)
            .param("offlineActionId", offlineActionId)
            .query((rs, rowNum) -> new SyncActionReceiptRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("client_id"),
                rs.getString("offline_action_id"),
                rs.getString("action_type"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getObject("trace_id", UUID.class),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                jsonSupport.readMap(rs.getString("payload")),
                timestampToInstant(rs.getTimestamp("occurred_at")),
                timestampToInstant(rs.getTimestamp("processed_at"))
            ))
            .optional();
    }

    @Override
    public SyncActionReservation reserveProcessing(SyncActionReceiptReserveCommand command) {
        int inserted = jdbcClient.sql("""
            insert into sync_action_receipts (
                id, user_id, client_id, offline_action_id, action_type, entity_type, entity_id, trace_id,
                status, error_code, error_message, payload, occurred_at
            ) values (
                :id, :userId, :clientId, :offlineActionId, :actionType, :entityType, :entityId, :traceId,
                :status, null, null, cast(:payload as jsonb), :occurredAt
            )
            on conflict (user_id, client_id, offline_action_id) do nothing
            """)
            .param("id", UUID.randomUUID())
            .param("userId", command.userId())
            .param("clientId", command.clientId())
            .param("offlineActionId", command.offlineActionId())
            .param("actionType", command.actionType())
            .param("entityType", command.entityType())
            .param("entityId", command.entityId())
            .param("traceId", command.traceId())
            .param("status", "PROCESSING")
            .param("payload", jsonSupport.write(command.payload() == null ? Map.of() : command.payload()))
            .param("occurredAt", command.occurredAt())
            .update();

        SyncActionReceiptRecord record = findByIdempotencyKey(
            command.userId(),
            command.clientId(),
            command.offlineActionId()
        ).orElseThrow();
        return new SyncActionReservation(record, inserted > 0);
    }

    @Override
    public SyncActionReceiptRecord updateOutcome(SyncActionReceiptOutcomeCommand command) {
        jdbcClient.sql("""
            update sync_action_receipts
            set status = :status,
                trace_id = :traceId,
                error_code = :errorCode,
                error_message = :errorMessage,
                processed_at = current_timestamp
            where user_id = :userId
              and client_id = :clientId
              and offline_action_id = :offlineActionId
              and status = 'PROCESSING'
            """)
            .param("userId", command.userId())
            .param("clientId", command.clientId())
            .param("offlineActionId", command.offlineActionId())
            .param("traceId", command.traceId())
            .param("status", command.status())
            .param("errorCode", command.errorCode())
            .param("errorMessage", command.errorMessage())
            .update();

        return findByIdempotencyKey(
            command.userId(),
            command.clientId(),
            command.offlineActionId()
        ).orElseThrow();
    }

    @Override
    public void deleteByIdempotencyKey(UUID userId, String clientId, String offlineActionId) {
        jdbcClient.sql("""
            delete from sync_action_receipts
            where user_id = :userId
              and client_id = :clientId
              and offline_action_id = :offlineActionId
              and status = 'PROCESSING'
            """)
            .param("userId", userId)
            .param("clientId", clientId)
            .param("offlineActionId", offlineActionId)
            .update();
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
