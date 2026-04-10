package com.noteops.agent.controller.idea;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.idea.AssessIdeaRequest;
import com.noteops.agent.dto.idea.CreateIdeaRequest;
import com.noteops.agent.dto.idea.IdeaDetailResponse;
import com.noteops.agent.dto.idea.IdeaResponse;
import com.noteops.agent.dto.idea.IdeaSummaryResponse;
import com.noteops.agent.dto.idea.GenerateIdeaTaskRequest;
import com.noteops.agent.dto.idea.IdeaTaskGenerationResponse;
import com.noteops.agent.service.idea.IdeaApplicationService;
import com.noteops.agent.service.idea.IdeaAssessmentService;
import com.noteops.agent.service.idea.IdeaQueryService;
import com.noteops.agent.service.idea.IdeaTaskGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ideas")
public class IdeaController {

    private static final Logger log = LoggerFactory.getLogger(IdeaController.class);

    private final IdeaApplicationService ideaApplicationService;
    private final IdeaAssessmentService ideaAssessmentService;
    private final IdeaQueryService ideaQueryService;
    private final IdeaTaskGenerationService ideaTaskGenerationService;

    public IdeaController(IdeaApplicationService ideaApplicationService,
                          IdeaAssessmentService ideaAssessmentService,
                          IdeaQueryService ideaQueryService,
                          IdeaTaskGenerationService ideaTaskGenerationService) {
        this.ideaApplicationService = ideaApplicationService;
        this.ideaAssessmentService = ideaAssessmentService;
        this.ideaQueryService = ideaQueryService;
        this.ideaTaskGenerationService = ideaTaskGenerationService;
    }

    // 查询 Idea 列表，供单页工作台中的 Idea List 区块复用。
    @GetMapping
    public ApiEnvelope<List<IdeaSummaryResponse>> list(@RequestParam("user_id") String userId) {
        List<IdeaSummaryResponse> ideas = ideaQueryService.list(userId).stream()
            .map(IdeaSummaryResponse::from)
            .toList();
        return ApiEnvelope.success(null, ideas);
    }

    // 查询 Idea 详情，供 Idea Detail 区块展示 assessment 与动作入口。
    @GetMapping("/{id}")
    public ApiEnvelope<IdeaDetailResponse> get(@PathVariable String id, @RequestParam("user_id") String userId) {
        IdeaQueryService.IdeaDetailView ideaView = ideaQueryService.get(id, userId);
        return ApiEnvelope.success(null, IdeaDetailResponse.from(ideaView));
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

    // 执行 Idea assess，生成结构化评估并推进状态到 ASSESSED。
    @PostMapping("/{ideaId}/assess")
    public ResponseEntity<ApiEnvelope<IdeaResponse>> assess(@PathVariable String ideaId,
                                                            @RequestBody AssessIdeaRequest request) {
        log.info(
            "module=IdeaController action=idea_assess_request_received path=/api/v1/ideas/{}/assess user_id={} idea_id={}",
            ideaId,
            request.userId(),
            ideaId
        );
        IdeaAssessmentService.IdeaAssessmentCommandResult result = ideaAssessmentService.assess(
            new IdeaAssessmentService.AssessIdeaCommand(ideaId, request.userId())
        );
        return ResponseEntity.ok(ApiEnvelope.success(result.traceId(), IdeaResponse.from(result.idea())));
    }

    // 显式从 assessed Idea 派生 system tasks，并将 Idea 推进到 PLANNED。
    @PostMapping("/{ideaId}/generate-task")
    public ResponseEntity<ApiEnvelope<IdeaTaskGenerationResponse>> generateTask(@PathVariable String ideaId,
                                                                                @RequestBody GenerateIdeaTaskRequest request) {
        log.info(
            "module=IdeaController action=idea_task_generate_request_received path=/api/v1/ideas/{}/generate-task user_id={} idea_id={}",
            ideaId,
            request.userId(),
            ideaId
        );
        IdeaTaskGenerationService.IdeaTaskGenerationResult result = ideaTaskGenerationService.generate(
            new IdeaTaskGenerationService.GenerateIdeaTasksCommand(ideaId, request.userId())
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), IdeaTaskGenerationResponse.from(result.idea(), result.tasks())));
    }
}
