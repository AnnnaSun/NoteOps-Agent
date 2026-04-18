package com.noteops.agent.dto.sync;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record SyncActionsRequest(
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("client_id")
    String clientId,
    @JsonProperty("actions")
    List<SyncActionRequestItem> actions
) {
    public record SyncActionRequestItem(
        @JsonProperty("offline_action_id")
        String offlineActionId,
        @JsonProperty("action_type")
        String actionType,
        @JsonProperty("entity_type")
        String entityType,
        @JsonProperty("entity_id")
        String entityId,
        @JsonProperty("payload")
        Map<String, Object> payload,
        @JsonProperty("occurred_at")
        String occurredAt
    ) {
    }
}
