package com.noteops.agent.controller.search;

import com.noteops.agent.dto.search.SearchResponse;
import com.noteops.agent.dto.search.SearchEvidenceResponse;
import com.noteops.agent.dto.search.SearchSupplementActionRequest;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.search.SearchApplicationService;
import com.noteops.agent.service.search.SearchGovernanceApplicationService;
import com.noteops.agent.dto.proposal.ChangeProposalResponse;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchApplicationService searchApplicationService;
    private final SearchGovernanceApplicationService searchGovernanceApplicationService;

    public SearchController(SearchApplicationService searchApplicationService,
                            SearchGovernanceApplicationService searchGovernanceApplicationService) {
        this.searchApplicationService = searchApplicationService;
        this.searchGovernanceApplicationService = searchGovernanceApplicationService;
    }

    // 搜索入口：按关键词检索 Note，并返回聚合后的结果。
    @GetMapping
    public ApiEnvelope<SearchResponse> search(@RequestParam("user_id") String userId,
                                              @RequestParam("query") String query) {
        log.info("module=SearchController action=search_request path=/api/v1/search user_id={} query_length={} trace_id=null",
            userId,
            query == null ? 0 : query.length());
        SearchApplicationService.SearchView view = searchApplicationService.search(userId, query);
        return ApiEnvelope.success(null, SearchResponse.from(view));
    }

    // 将 search external supplement 保存为某个 Note 的 evidence block。
    @PostMapping("/notes/{noteId}/evidence")
    public ResponseEntity<ApiEnvelope<SearchEvidenceResponse>> saveEvidence(@PathVariable String noteId,
                                                                            @RequestBody SearchSupplementActionRequest request) {
        log.info("module=SearchController action=search_evidence_save_request path=/api/v1/search/notes/{noteId}/evidence note_id={} user_id={} source_uri={}",
            noteId, request.userId(), request.sourceUri());
        SearchGovernanceApplicationService.SearchEvidenceCommandResult result =
            searchGovernanceApplicationService.saveEvidence(
                noteId,
                new SearchGovernanceApplicationService.SaveSearchEvidenceCommand(
                    request.userId(),
                    request.query(),
                    request.sourceName(),
                    request.sourceUri(),
                    request.summary(),
                    request.keywords(),
                    request.relationLabel(),
                    request.relationTags(),
                    request.summarySnippet()
                )
            );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), SearchEvidenceResponse.from(result.evidence())));
    }

    // 基于 search external supplement 为某个 Note 生成 proposal 候选。
    @PostMapping("/notes/{noteId}/change-proposals")
    public ResponseEntity<ApiEnvelope<ChangeProposalResponse>> generateProposal(@PathVariable String noteId,
                                                                                @RequestBody SearchSupplementActionRequest request) {
        log.info("module=SearchController action=search_proposal_generate_request path=/api/v1/search/notes/{noteId}/change-proposals note_id={} user_id={} source_uri={}",
            noteId, request.userId(), request.sourceUri());
        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            searchGovernanceApplicationService.generateProposal(
                noteId,
                new SearchGovernanceApplicationService.GenerateSearchProposalCommand(
                    request.userId(),
                    request.query(),
                    request.sourceName(),
                    request.sourceUri(),
                    request.summary(),
                    request.keywords(),
                    request.relationLabel(),
                    request.relationTags(),
                    request.summarySnippet()
                )
            );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), ChangeProposalResponse.from(result.proposal())));
    }
}
