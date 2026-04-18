package com.noteops.agent.controller.sync;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.sync.SyncActionsRequest;
import com.noteops.agent.dto.sync.SyncActionsResponse;
import com.noteops.agent.service.sync.SyncActionsApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncActionsApplicationService syncActionsApplicationService;

    public SyncController(SyncActionsApplicationService syncActionsApplicationService) {
        this.syncActionsApplicationService = syncActionsApplicationService;
    }

    @PostMapping("/actions")
    public ApiEnvelope<SyncActionsResponse> actions(@RequestBody SyncActionsRequest request) {
        log.info(
            "action=sync_actions_request path=/api/v1/sync/actions user_id={} client_id={} action_count={}",
            request.userId(),
            request.clientId(),
            request.actions() == null ? 0 : request.actions().size()
        );

        SyncActionsApplicationService.SyncActionsResult result = syncActionsApplicationService.apply(
            new SyncActionsApplicationService.SyncActionsCommand(
                request.userId(),
                request.clientId(),
                request.actions() == null
                    ? null
                    : request.actions().stream()
                    .map(item -> new SyncActionsApplicationService.SyncActionCommand(
                        item.offlineActionId(),
                        item.actionType(),
                        item.entityType(),
                        item.entityId(),
                        item.payload(),
                        item.occurredAt()
                    ))
                    .toList()
            )
        );

        return ApiEnvelope.success(result.traceId(), SyncActionsResponse.from(result));
    }
}
