package com.noteops.agent.controller.trend;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.trend.TrendPlanTriggerRequest;
import com.noteops.agent.dto.trend.TrendPlanTriggerResponse;
import com.noteops.agent.service.trend.TrendPlanApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trends/plans/default")
public class TrendPlanController {

    private static final Logger log = LoggerFactory.getLogger(TrendPlanController.class);

    private final TrendPlanApplicationService trendPlanApplicationService;

    public TrendPlanController(TrendPlanApplicationService trendPlanApplicationService) {
        this.trendPlanApplicationService = trendPlanApplicationService;
    }

    @PostMapping("/trigger")
    public ApiEnvelope<TrendPlanTriggerResponse> triggerDefaultPlan(@RequestBody TrendPlanTriggerRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.info(
            "module=TrendPlanController action=trend_plan_trigger_request path=/api/v1/trends/plans/default/trigger user_id={} trace_id={}",
            request.userId(),
            traceId
        );
        TrendPlanApplicationService.TriggerResult result = trendPlanApplicationService.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand(request.userId(), traceId)
        );
        return ApiEnvelope.success(result.traceId(), TrendPlanTriggerResponse.from(result));
    }
}
