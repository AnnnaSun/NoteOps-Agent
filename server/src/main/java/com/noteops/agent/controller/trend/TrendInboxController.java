package com.noteops.agent.controller.trend;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.trend.TrendInboxItemResponse;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.service.trend.TrendInboxQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trends")
public class TrendInboxController {

    private static final Logger log = LoggerFactory.getLogger(TrendInboxController.class);

    private final TrendInboxQueryService trendInboxQueryService;

    public TrendInboxController(TrendInboxQueryService trendInboxQueryService) {
        this.trendInboxQueryService = trendInboxQueryService;
    }

    @GetMapping("/inbox")
    public ApiEnvelope<List<TrendInboxItemResponse>> inbox(@RequestParam("user_id") String userId,
                                                            @RequestParam(value = "status", required = false) String status,
                                                            @RequestParam(value = "source_type", required = false) String sourceType) {
        String traceId = UUID.randomUUID().toString();
        String effectiveStatus = status == null || status.isBlank() ? TrendItemStatus.ANALYZED.name() : status.trim();
        log.info(
            "module=TrendInboxController action=trend_inbox_request path=/api/v1/trends/inbox trace_id={} user_id={} status={} source_type={}",
            traceId,
            userId,
            effectiveStatus,
            sourceType
        );
        List<TrendInboxItemResponse> items = trendInboxQueryService.list(
            new TrendInboxQueryService.InboxQueryCommand(userId, effectiveStatus, sourceType, traceId)
        );
        return ApiEnvelope.success(traceId, items);
    }
}
