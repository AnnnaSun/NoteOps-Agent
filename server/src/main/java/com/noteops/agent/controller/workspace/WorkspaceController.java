package com.noteops.agent.controller.workspace;

import com.noteops.agent.dto.workspace.WorkspaceTodayResponse;
import com.noteops.agent.dto.workspace.WorkspaceUpcomingResponse;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.workspace.WorkspaceApplicationService;
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

    // 聚合 Workspace 今日视图，返回 review + task。
    @GetMapping("/today")
    public ApiEnvelope<WorkspaceTodayResponse> today(@RequestParam("user_id") String userId,
                                                     @RequestParam(value = "timezone_offset", required = false) String timezoneOffset) {
        log.info("action=workspace_today_request user_id={} timezone_offset={}", userId, timezoneOffset);
        WorkspaceApplicationService.WorkspaceTodayView view = workspaceApplicationService.today(userId, timezoneOffset);
        return ApiEnvelope.success(null, WorkspaceTodayResponse.from(view));
    }

    // 聚合 Workspace upcoming 视图，返回 review + task。
    @GetMapping("/upcoming")
    public ApiEnvelope<WorkspaceUpcomingResponse> upcoming(@RequestParam("user_id") String userId,
                                                           @RequestParam(value = "timezone_offset", required = false) String timezoneOffset) {
        log.info("action=workspace_upcoming_request user_id={} timezone_offset={}", userId, timezoneOffset);
        WorkspaceApplicationService.WorkspaceUpcomingView view = workspaceApplicationService.upcoming(userId, timezoneOffset);
        return ApiEnvelope.success(null, WorkspaceUpcomingResponse.from(view));
    }
}
