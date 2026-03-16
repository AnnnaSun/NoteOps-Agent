package com.noteops.agent.api.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.proposal.ChangeProposalApplicationService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChangeProposalResponse(
    String id,
    @JsonProperty("user_id")
    String userId,
    @JsonProperty("note_id")
    String noteId,
    @JsonProperty("trace_id")
    String traceId,
    @JsonProperty("proposal_type")
    String proposalType,
    @JsonProperty("target_layer")
    String targetLayer,
    @JsonProperty("risk_level")
    String riskLevel,
    @JsonProperty("diff_summary")
    String diffSummary,
    @JsonProperty("before_snapshot")
    Map<String, Object> beforeSnapshot,
    @JsonProperty("after_snapshot")
    Map<String, Object> afterSnapshot,
    @JsonProperty("source_refs")
    List<Map<String, Object>> sourceRefs,
    String status,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("updated_at")
    Instant updatedAt
) {

    public static ChangeProposalResponse from(ChangeProposalApplicationService.ChangeProposalView view) {
        return new ChangeProposalResponse(
            view.id().toString(),
            view.userId().toString(),
            view.noteId().toString(),
            view.traceId() == null ? null : view.traceId().toString(),
            view.proposalType(),
            view.targetLayer().name(),
            view.riskLevel().name(),
            view.diffSummary(),
            view.beforeSnapshot(),
            view.afterSnapshot(),
            view.sourceRefs(),
            view.status().name(),
            view.createdAt(),
            view.updatedAt()
        );
    }
}
