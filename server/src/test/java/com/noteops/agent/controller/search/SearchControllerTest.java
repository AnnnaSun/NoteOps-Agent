package com.noteops.agent.controller.search;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import com.noteops.agent.service.search.SearchApplicationService;
import com.noteops.agent.service.search.SearchGovernanceApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import(ApiExceptionHandler.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchApplicationService searchApplicationService;

    @MockBean
    private SearchGovernanceApplicationService searchGovernanceApplicationService;

    @Test
    void returnsThreeBucketEnvelope() throws Exception {
        UUID noteId = UUID.randomUUID();
        when(searchApplicationService.search("11111111-1111-1111-1111-111111111111", "kickoff alpha"))
            .thenReturn(new SearchApplicationService.SearchView(
                "kickoff alpha",
                List.of(
                    new SearchApplicationService.SearchExactMatchView(
                        noteId,
                        "Kickoff alpha",
                        "Summary",
                        List.of("point-1"),
                        "Latest content",
                        Instant.parse("2026-03-16T01:00:00Z")
                    )
                ),
                List.of(
                    new SearchApplicationService.SearchRelatedMatchView(
                        UUID.randomUUID(),
                        "Kickoff review",
                        "Related summary",
                        List.of("point-2"),
                        "Related content",
                        "共享标题主题：kickoff",
                        Instant.parse("2026-03-16T02:00:00Z"),
                        true
                    )
                ),
                List.of(
                    new SearchApplicationService.ExternalSupplementView(
                        "Search Stub Background",
                        "stub://search/background?q=kickoff+alpha",
                        "Background reading related to kickoff alpha",
                        List.of("kickoff", "alpha"),
                        List.of("BACKGROUND"),
                        "背景补充",
                        "背景资料聚焦 kickoff alpha",
                        false
                    )
                ),
                "DEGRADED"
            ));

        mockMvc.perform(get("/api/v1/search")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("query", "kickoff alpha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.query").value("kickoff alpha"))
            .andExpect(jsonPath("$.data.exact_matches[0].note_id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.related_matches[0].relation_reason").value("共享标题主题：kickoff"))
            .andExpect(jsonPath("$.data.related_matches[0].is_ai_enhanced").value(true))
            .andExpect(jsonPath("$.data.external_supplements[0].source_name").value("Search Stub Background"))
            .andExpect(jsonPath("$.data.external_supplements[0].relation_label").value("背景补充"))
            .andExpect(jsonPath("$.data.external_supplements[0].summary_snippet").value("背景资料聚焦 kickoff alpha"))
            .andExpect(jsonPath("$.data.external_supplements[0].is_ai_enhanced").value(false))
            .andExpect(jsonPath("$.data.ai_enhancement_status").value("DEGRADED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void savesEvidenceFromSearch() throws Exception {
        UUID noteId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        when(searchGovernanceApplicationService.saveEvidence(any(), any()))
            .thenReturn(new SearchGovernanceApplicationService.SearchEvidenceCommandResult(
                new SearchGovernanceApplicationService.SearchEvidenceSaveView(
                    noteId,
                    contentId,
                    "EVIDENCE",
                    "stub://search/background?q=kickoff+alpha",
                    "背景补充"
                ),
                "trace-search-evidence"
            ));

        mockMvc.perform(post("/api/v1/search/notes/{noteId}/evidence", noteId)
                .contentType("application/json")
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "query": "kickoff alpha",
                      "source_name": "Search Stub Background",
                      "source_uri": "stub://search/background?q=kickoff+alpha",
                      "summary": "Background reading related to kickoff alpha",
                      "keywords": ["kickoff", "alpha"],
                      "relation_label": "背景补充",
                      "relation_tags": ["BACKGROUND"],
                      "summary_snippet": "背景资料聚焦 kickoff alpha"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.trace_id").value("trace-search-evidence"))
            .andExpect(jsonPath("$.data.note_id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.content_id").value(contentId.toString()))
            .andExpect(jsonPath("$.data.content_type").value("EVIDENCE"));
    }

    @Test
    void createsProposalFromSearch() throws Exception {
        UUID noteId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        when(searchGovernanceApplicationService.generateProposal(any(), any()))
            .thenReturn(new ChangeProposalApplicationService.ChangeProposalCommandResult(
                new ChangeProposalApplicationService.ChangeProposalView(
                    proposalId,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    noteId,
                    traceId,
                    "SEARCH_EVIDENCE_REFRESH_INTERPRETATION",
                    ChangeProposalTargetLayer.INTERPRETATION,
                    ChangeProposalRiskLevel.LOW,
                    "背景补充：根据外部补充刷新 current_summary 与 current_key_points。",
                    Map.of("current_summary", "before", "current_key_points", List.of("one")),
                    Map.of("current_summary", "after", "current_key_points", List.of("one", "two")),
                    List.of(Map.of("source_uri", "stub://search/background?q=kickoff+alpha")),
                    null,
                    ChangeProposalStatus.PENDING_REVIEW,
                    Instant.parse("2026-03-20T01:00:00Z"),
                    Instant.parse("2026-03-20T01:00:00Z")
                ),
                "trace-search-proposal"
            ));

        mockMvc.perform(post("/api/v1/search/notes/{noteId}/change-proposals", noteId)
                .contentType("application/json")
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "query": "kickoff alpha",
                      "source_name": "Search Stub Background",
                      "source_uri": "stub://search/background?q=kickoff+alpha",
                      "summary": "Background reading related to kickoff alpha",
                      "keywords": ["kickoff", "alpha"],
                      "relation_label": "背景补充",
                      "relation_tags": ["BACKGROUND"],
                      "summary_snippet": "背景资料聚焦 kickoff alpha"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.trace_id").value("trace-search-proposal"))
            .andExpect(jsonPath("$.data.id").value(proposalId.toString()))
            .andExpect(jsonPath("$.data.note_id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.proposal_type").value("SEARCH_EVIDENCE_REFRESH_INTERPRETATION"));
    }

    @Test
    void returnsErrorEnvelopeForBlankQuery() throws Exception {
        when(searchApplicationService.search("11111111-1111-1111-1111-111111111111", " "))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must not be blank"));

        mockMvc.perform(get("/api/v1/search")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("query", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_SEARCH_QUERY"))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
