package com.noteops.agent.controller.proposal;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChangeProposalController.class)
@Import(ApiExceptionHandler.class)
class ChangeProposalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChangeProposalApplicationService changeProposalApplicationService;

    @Test
    void createsProposalWithEnvelope() throws Exception {
        UUID proposalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(changeProposalApplicationService.generate(eq(noteId.toString()), eq(userId.toString())))
            .thenReturn(new ChangeProposalApplicationService.ChangeProposalCommandResult(
                view(proposalId, noteId, userId, ChangeProposalStatus.PENDING_REVIEW),
                "trace-proposal-create"
            ));

        mockMvc.perform(post("/api/v1/notes/{noteId}/change-proposals", noteId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s"
                    }
                    """.formatted(userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-proposal-create"))
            .andExpect(jsonPath("$.data.id").value(proposalId.toString()))
            .andExpect(jsonPath("$.data.target_layer").value("INTERPRETATION"))
            .andExpect(jsonPath("$.data.risk_level").value("LOW"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void listsProposalsForNote() throws Exception {
        UUID proposalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(changeProposalApplicationService.listByNote(noteId.toString(), userId.toString()))
            .thenReturn(List.of(view(proposalId, noteId, userId, ChangeProposalStatus.PENDING_REVIEW)));

        mockMvc.perform(get("/api/v1/notes/{noteId}/change-proposals", noteId)
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data[0].id").value(proposalId.toString()))
            .andExpect(jsonPath("$.data[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void appliesProposalWithTraceId() throws Exception {
        UUID proposalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(changeProposalApplicationService.apply(noteId.toString(), proposalId.toString(), userId.toString()))
            .thenReturn(new ChangeProposalApplicationService.ChangeProposalCommandResult(
                view(proposalId, noteId, userId, ChangeProposalStatus.APPLIED),
                "trace-proposal-apply"
            ));

        mockMvc.perform(post("/api/v1/notes/{noteId}/change-proposals/{proposalId}/apply", noteId, proposalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s"
                    }
                    """.formatted(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-proposal-apply"))
            .andExpect(jsonPath("$.data.rollback_token").value("rollback-token"))
            .andExpect(jsonPath("$.data.before_snapshot_summary").value("current_summary=before; current_key_points=[before-point]"))
            .andExpect(jsonPath("$.data.after_snapshot_summary").value("current_summary=after; current_key_points=[after-point]"))
            .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    void rollsBackProposalWithTraceId() throws Exception {
        UUID proposalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(changeProposalApplicationService.rollback(proposalId.toString(), userId.toString()))
            .thenReturn(new ChangeProposalApplicationService.ChangeProposalCommandResult(
                view(proposalId, noteId, userId, ChangeProposalStatus.ROLLED_BACK),
                "trace-proposal-rollback"
            ));

        mockMvc.perform(post("/api/v1/change-proposals/{proposalId}/rollback", proposalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s"
                    }
                    """.formatted(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-proposal-rollback"))
            .andExpect(jsonPath("$.data.rollback_token").value("rollback-token"))
            .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"));
    }

    @Test
    void returnsConflictEnvelopeForAlreadyAppliedProposal() throws Exception {
        when(changeProposalApplicationService.apply(eq("bad-note"), eq("bad-proposal"), any()))
            .thenThrow(new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_APPLIED", "change proposal is already applied"));

        mockMvc.perform(post("/api/v1/notes/{noteId}/change-proposals/{proposalId}/apply", "bad-note", "bad-proposal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHANGE_PROPOSAL_ALREADY_APPLIED"));
    }

    private ChangeProposalApplicationService.ChangeProposalView view(UUID proposalId,
                                                                     UUID noteId,
                                                                     UUID userId,
                                                                     ChangeProposalStatus status) {
        return new ChangeProposalApplicationService.ChangeProposalView(
            proposalId,
            userId,
            noteId,
            UUID.randomUUID(),
            "REFRESH_INTERPRETATION",
            ChangeProposalTargetLayer.INTERPRETATION,
            ChangeProposalRiskLevel.LOW,
            "Refresh current_summary and current_key_points from the latest note content.",
            Map.of("current_summary", "before", "current_key_points", List.of("before-point")),
            Map.of("current_summary", "after", "current_key_points", List.of("after-point")),
            List.of(Map.of("content_type", "CAPTURE_RAW")),
            status == ChangeProposalStatus.PENDING_REVIEW ? null : "rollback-token",
            status,
            Instant.parse("2026-03-16T01:00:00Z"),
            Instant.parse("2026-03-16T01:05:00Z")
        );
    }
}
