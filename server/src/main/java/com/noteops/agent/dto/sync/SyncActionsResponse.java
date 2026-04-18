package com.noteops.agent.dto.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.sync.SyncActionsApplicationService;

import java.time.Instant;
import java.util.List;

public record SyncActionsResponse(
    @JsonProperty("accepted")
    List<AcceptedSyncActionResponseItem> accepted,
    @JsonProperty("rejected")
    List<RejectedSyncActionResponseItem> rejected,
    @JsonProperty("server_sync_cursor")
    Instant serverSyncCursor
) {
    public static SyncActionsResponse from(SyncActionsApplicationService.SyncActionsResult result) {
        return new SyncActionsResponse(
            result.accepted().stream().map(AcceptedSyncActionResponseItem::from).toList(),
            result.rejected().stream().map(RejectedSyncActionResponseItem::from).toList(),
            result.serverSyncCursor()
        );
    }

    public record AcceptedSyncActionResponseItem(
        @JsonProperty("offline_action_id")
        String offlineActionId,
        @JsonProperty("action_type")
        String actionType,
        @JsonProperty("entity_type")
        String entityType,
        @JsonProperty("entity_id")
        String entityId,
        @JsonProperty("duplicated")
        boolean duplicated
    ) {
        private static AcceptedSyncActionResponseItem from(SyncActionsApplicationService.AcceptedActionView view) {
            return new AcceptedSyncActionResponseItem(
                view.offlineActionId(),
                view.actionType(),
                view.entityType(),
                view.entityId() == null ? null : view.entityId().toString(),
                view.duplicated()
            );
        }
    }

    public record RejectedSyncActionResponseItem(
        @JsonProperty("offline_action_id")
        String offlineActionId,
        @JsonProperty("action_type")
        String actionType,
        @JsonProperty("entity_type")
        String entityType,
        @JsonProperty("entity_id")
        String entityId,
        @JsonProperty("error_code")
        String errorCode,
        @JsonProperty("error_message")
        String errorMessage,
        @JsonProperty("retryable")
        boolean retryable,
        @JsonProperty("duplicated")
        boolean duplicated
    ) {
        private static RejectedSyncActionResponseItem from(SyncActionsApplicationService.RejectedActionView view) {
            return new RejectedSyncActionResponseItem(
                view.offlineActionId(),
                view.actionType(),
                view.entityType(),
                view.entityId() == null ? null : view.entityId().toString(),
                view.errorCode(),
                view.errorMessage(),
                view.retryable(),
                view.duplicated()
            );
        }
    }
}
