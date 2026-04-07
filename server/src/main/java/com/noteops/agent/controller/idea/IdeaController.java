package com.noteops.agent.controller.idea;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.idea.CreateIdeaRequest;
import com.noteops.agent.dto.idea.IdeaResponse;
import com.noteops.agent.service.idea.IdeaApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ideas")
public class IdeaController {

    private static final Logger log = LoggerFactory.getLogger(IdeaController.class);

    private final IdeaApplicationService ideaApplicationService;

    public IdeaController(IdeaApplicationService ideaApplicationService) {
        this.ideaApplicationService = ideaApplicationService;
    }

    // 创建 Idea，支持独立创建和基于 Note 派生两种最小来源模式。
    @PostMapping
    public ResponseEntity<ApiEnvelope<IdeaResponse>> create(@RequestBody CreateIdeaRequest request) {
        log.info(
            "action=idea_create_request_received user_id={} source_mode={} source_note_id={}",
            request.userId(),
            request.sourceMode(),
            request.sourceNoteId()
        );
        IdeaApplicationService.IdeaCommandResult result = ideaApplicationService.create(
            new IdeaApplicationService.CreateIdeaCommand(
                request.userId(),
                request.sourceMode(),
                request.sourceNoteId(),
                request.title(),
                request.rawDescription()
            )
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), IdeaResponse.from(result.idea())));
    }
}
