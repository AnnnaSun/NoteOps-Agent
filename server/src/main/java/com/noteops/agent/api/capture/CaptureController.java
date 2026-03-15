package com.noteops.agent.api.capture;

import com.noteops.agent.api.ApiEnvelope;
import com.noteops.agent.application.capture.CaptureApplicationService;
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

    private final CaptureApplicationService captureApplicationService;

    public CaptureController(CaptureApplicationService captureApplicationService) {
        this.captureApplicationService = captureApplicationService;
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<CaptureResponse>> create(@RequestBody CreateCaptureRequest request) {
        CaptureApplicationService.CaptureView captureView = captureApplicationService.create(
            new CaptureApplicationService.CreateCaptureCommand(
                request.userId(),
                request.inputType(),
                request.rawInput(),
                request.sourceUri()
            )
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(captureView.traceId(), CaptureResponse.from(captureView)));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<CaptureResponse> get(@PathVariable String id, @RequestParam("user_id") String userId) {
        CaptureApplicationService.CaptureView captureView = captureApplicationService.get(id, userId);
        return ApiEnvelope.success(captureView.traceId(), CaptureResponse.from(captureView));
    }
}
