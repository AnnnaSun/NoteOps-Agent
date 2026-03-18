package com.noteops.agent.api.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.application.proposal.ChangeProposalApplicationService;

import java.time.Instant;
import java.util.ArrayList;
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
    @JsonProperty("before_snapshot_summary")
    String beforeSnapshotSummary,
    @JsonProperty("after_snapshot_summary")
    String afterSnapshotSummary,
    @JsonProperty("source_refs")
    List<Map<String, Object>> sourceRefs,
    @JsonProperty("rollback_token")
    String rollbackToken,
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
            snapshotSummary(view.beforeSnapshot()),
            snapshotSummary(view.afterSnapshot()),
            view.sourceRefs(),
            view.rollbackToken(),
            view.status().name(),
            view.createdAt(),
            view.updatedAt()
        );
    }

    private static String snapshotSummary(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        appendPart(parts, "current_summary", snapshot.get("current_summary"));
        appendPart(parts, "current_key_points", snapshot.get("current_key_points"));

        if (parts.isEmpty()) {
            return snapshot.toString();
        }
        return String.join("; ", parts);
    }

    private static void appendPart(List<String> parts, String label, Object value) {
        if (value == null) {
            return;
        }
        parts.add(label + "=" + value);
    }
}
