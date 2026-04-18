package com.noteops.agent.repository.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserActionEventRepository {

    void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload);

    default List<UserActionEventRecord> findRecentByUserId(UUID userId, int limit) {
        throw new UnsupportedOperationException("findRecentByUserId must be implemented");
    }

    record UserActionEventRecord(
        UUID id,
        UUID userId,
        String eventType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload,
        Instant createdAt
    ) {
    }
}
