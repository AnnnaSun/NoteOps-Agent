package com.noteops.agent.controller.proposal;

import com.noteops.agent.dto.proposal.ApplyChangeProposalRequest;
import com.noteops.agent.dto.proposal.ChangeProposalResponse;
import com.noteops.agent.dto.proposal.CreateChangeProposalRequest;
import com.noteops.agent.dto.proposal.RollbackChangeProposalRequest;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChangeProposalController {

    private static final Logger log = LoggerFactory.getLogger(ChangeProposalController.class);

    private final ChangeProposalApplicationService changeProposalApplicationService;

    public ChangeProposalController(ChangeProposalApplicationService changeProposalApplicationService) {
        this.changeProposalApplicationService = changeProposalApplicationService;
    }

    // 基于 Note 生成变更建议，写入 proposal 治理链路。
    @PostMapping("/notes/{noteId}/change-proposals")
    public ResponseEntity<ApiEnvelope<ChangeProposalResponse>> create(@PathVariable String noteId,
                                                                      @RequestBody CreateChangeProposalRequest request) {
        log.info("action=change_proposal_create_request note_id={} user_id={}", noteId, request.userId());
        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            changeProposalApplicationService.generate(noteId, request.userId());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), ChangeProposalResponse.from(result.proposal())));
    }

    // 查询某个 Note 下的所有 proposal，供详情页展示。
    @GetMapping("/notes/{noteId}/change-proposals")
    public ApiEnvelope<List<ChangeProposalResponse>> list(@PathVariable String noteId,
                                                          @RequestParam("user_id") String userId) {
        log.info("action=change_proposal_list_request note_id={} user_id={}", noteId, userId);
        List<ChangeProposalResponse> proposals = changeProposalApplicationService.listByNote(noteId, userId)
            .stream()
            .map(ChangeProposalResponse::from)
            .toList();
        return ApiEnvelope.success(null, proposals);
    }

    // 应用 proposal 到 Note 的解释层，并记录 trace/event。
    @PostMapping("/notes/{noteId}/change-proposals/{proposalId}/apply")
    public ApiEnvelope<ChangeProposalResponse> apply(@PathVariable String noteId,
                                                     @PathVariable String proposalId,
                                                     @RequestBody ApplyChangeProposalRequest request) {
        log.info("action=change_proposal_apply_request note_id={} proposal_id={} user_id={}",
            noteId, proposalId, request.userId());
        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            changeProposalApplicationService.apply(noteId, proposalId, request.userId());
        return ApiEnvelope.success(result.traceId(), ChangeProposalResponse.from(result.proposal()));
    }

    // 回滚已应用的 proposal，恢复 Note 的解释层。
    @PostMapping("/change-proposals/{proposalId}/rollback")
    public ApiEnvelope<ChangeProposalResponse> rollback(@PathVariable String proposalId,
                                                        @RequestBody RollbackChangeProposalRequest request) {
        log.info("action=change_proposal_rollback_request proposal_id={} user_id={}", proposalId, request.userId());
        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            changeProposalApplicationService.rollback(proposalId, request.userId());
        return ApiEnvelope.success(result.traceId(), ChangeProposalResponse.from(result.proposal()));
    }
}
