package com.noteops.agent.controller.capture;

import com.noteops.agent.dto.capture.CaptureResponse;
import com.noteops.agent.dto.capture.CreateCaptureRequest;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.capture.CaptureApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/captures")
public class CaptureController {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);

    private final CaptureApplicationService captureApplicationService;

    public CaptureController(CaptureApplicationService captureApplicationService) {
        this.captureApplicationService = captureApplicationService;
    }

    // 创建 Capture 请求，先记录入口日志，再交给应用层编排。
    @PostMapping
    public ResponseEntity<ApiEnvelope<CaptureResponse>> create(@RequestBody CreateCaptureRequest request) {
        log.info(
            "module=CaptureController action=capture_request path=/api/v1/captures user_id={} source_type={} trace_id=null",
            request.userId(),
            request.sourceType()
        );
        CaptureApplicationService.CaptureView captureView = captureApplicationService.create(
            new CaptureApplicationService.CreateCaptureCommand(
                request.userId(),
                request.sourceType(),
                request.rawText(),
                request.sourceUrl(),
                request.titleHint()
            )
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(captureView.traceId() == null ? null : captureView.traceId().toString(), CaptureResponse.from(captureView)));
    }

    // 按 id 和 user_id 查询 Capture 详情，用于结果回看。
    @GetMapping("/{id}")
    public ApiEnvelope<CaptureResponse> get(@PathVariable String id, @RequestParam("user_id") String userId) {
        log.info(
            "module=CaptureController action=capture_get_request path=/api/v1/captures/{} user_id={} trace_id=null",
            id,
            userId
        );
        CaptureApplicationService.CaptureView captureView = captureApplicationService.get(id, userId);
        return ApiEnvelope.success(captureView.traceId() == null ? null : captureView.traceId().toString(), CaptureResponse.from(captureView));
    }
}
