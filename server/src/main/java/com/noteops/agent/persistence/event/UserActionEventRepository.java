package com.noteops.agent.persistence.event;

import java.util.Map;
import java.util.UUID;

public interface UserActionEventRepository {

    void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload);
}
