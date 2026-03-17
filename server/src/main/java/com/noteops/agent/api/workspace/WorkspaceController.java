package com.noteops.agent.api.workspace;

import com.noteops.agent.api.ApiEnvelope;
import com.noteops.agent.application.workspace.WorkspaceApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceApplicationService workspaceApplicationService;

    public WorkspaceController(WorkspaceApplicationService workspaceApplicationService) {
        this.workspaceApplicationService = workspaceApplicationService;
    }

    @GetMapping("/today")
    public ApiEnvelope<WorkspaceTodayResponse> today(@RequestParam("user_id") String userId,
                                                     @RequestParam(value = "timezone_offset", required = false) String timezoneOffset) {
        log.info("action=workspace_today_request user_id={} timezone_offset={}", userId, timezoneOffset);
        WorkspaceApplicationService.WorkspaceTodayView view = workspaceApplicationService.today(userId, timezoneOffset);
        return ApiEnvelope.success(null, WorkspaceTodayResponse.from(view));
    }

    @GetMapping("/upcoming")
    public ApiEnvelope<WorkspaceUpcomingResponse> upcoming(@RequestParam("user_id") String userId,
                                                           @RequestParam(value = "timezone_offset", required = false) String timezoneOffset) {
        log.info("action=workspace_upcoming_request user_id={} timezone_offset={}", userId, timezoneOffset);
        WorkspaceApplicationService.WorkspaceUpcomingView view = workspaceApplicationService.upcoming(userId, timezoneOffset);
        return ApiEnvelope.success(null, WorkspaceUpcomingResponse.from(view));
    }
}
