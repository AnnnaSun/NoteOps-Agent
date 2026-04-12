package com.noteops.agent.controller.trend;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.trend.TrendActionRequest;
import com.noteops.agent.dto.trend.TrendActionResponse;
import com.noteops.agent.service.trend.TrendActionApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trends")
public class TrendActionController {

    private static final Logger log = LoggerFactory.getLogger(TrendActionController.class);

    private final TrendActionApplicationService trendActionApplicationService;

    public TrendActionController(TrendActionApplicationService trendActionApplicationService) {
        this.trendActionApplicationService = trendActionApplicationService;
    }

    @PostMapping("/{trendItemId}/actions")
    public ApiEnvelope<TrendActionResponse> act(@PathVariable("trendItemId") String trendItemId,
                                                @RequestBody TrendActionRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.info(
            "module=TrendActionController action=trend_action_request path=/api/v1/trends/{trendItemId}/actions trace_id={} user_id={} trend_item_id={} action={} operator_note={}",
            traceId,
            request.userId(),
            trendItemId,
            request.action(),
            request.operatorNote()
        );
        TrendActionApplicationService.ActionResult result = trendActionApplicationService.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId,
                request.userId(),
                request.action(),
                request.operatorNote(),
                traceId
            )
        );
        log.info(
            "module=TrendActionController action=trend_action_success path=/api/v1/trends/{trendItemId}/actions trace_id={} user_id={} trend_item_id={} action_result={} result=COMPLETED",
            result.traceId(),
            request.userId(),
            result.trendItem().id(),
            result.trendItem().status().name()
        );
        return ApiEnvelope.success(result.traceId(), TrendActionResponse.from(result));
    }
}
