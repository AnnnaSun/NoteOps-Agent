package com.noteops.agent.service.proposal;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.service.note.NoteInterpretationSupport;
import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.proposal.ChangeProposalRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChangeProposalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChangeProposalApplicationService.class);
    private static final String PROPOSAL_TYPE = "REFRESH_INTERPRETATION";

    private final ChangeProposalRepository changeProposalRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    public ChangeProposalApplicationService(ChangeProposalRepository changeProposalRepository,
                                            NoteRepository noteRepository,
                                            AgentTraceRepository agentTraceRepository,
                                            ToolInvocationLogRepository toolInvocationLogRepository,
                                            UserActionEventRepository userActionEventRepository) {
        this.changeProposalRepository = changeProposalRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    @Transactional
    // 生成 proposal：基于最新 Note 内容构建 before/after 快照。
    public ChangeProposalCommandResult generate(String noteIdRaw, String userIdRaw) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        log.info("action=change_proposal_generate_start user_id={} note_id={}", userId, noteId);

        NoteQueryService.NoteDetailView note = noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));

        DraftInterpretation draft = draftInterpretation(note);
        UUID traceId = agentTraceRepository.create(
            userId,
            "CHANGE_PROPOSAL_GENERATE",
            "Generate change proposal for note " + noteId,
            "NOTE",
            noteId,
            List.of("proposal-worker"),
            Map.of("note_id", noteId, "proposal_type", PROPOSAL_TYPE)
        );

        ChangeProposalView proposal = changeProposalRepository.create(
            userId,
            noteId,
            traceId,
            PROPOSAL_TYPE,
            ChangeProposalTargetLayer.INTERPRETATION,
            ChangeProposalRiskLevel.LOW,
            "Refresh current_summary and current_key_points from the latest note content.",
            beforeSnapshot(note),
            afterSnapshot(draft),
            sourceRefs(note)
        );

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "proposal.interpretation-generator",
            "COMPLETED",
            Map.of("note_id", noteId),
            Map.of("proposal_id", proposal.id(), "risk_level", proposal.riskLevel().name()),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "CHANGE_PROPOSAL_CREATED",
            "CHANGE_PROPOSAL",
            proposal.id(),
            traceId,
            Map.of(
                "note_id", noteId,
                "target_layer", proposal.targetLayer().name(),
                "risk_level", proposal.riskLevel().name()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Generated change proposal " + proposal.id(),
            Map.of("proposal_id", proposal.id(), "note_id", noteId, "result", "CREATED")
        );

        log.info("action=change_proposal_generate_success user_id={} note_id={} proposal_id={} trace_id={}",
            userId, noteId, proposal.id(), traceId);
        return new ChangeProposalCommandResult(proposal, traceId.toString());
    }

    // 按 Note 查询 proposal 列表。
    public List<ChangeProposalView> listByNote(String noteIdRaw, String userIdRaw) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
        log.info("action=change_proposal_list user_id={} note_id={}", userId, noteId);
        return changeProposalRepository.findByNoteIdAndUserId(noteId, userId);
    }

    @Transactional
    // 应用 proposal：更新 Note 解释层并标记 proposal 为已应用。
    public ChangeProposalCommandResult apply(String noteIdRaw, String proposalIdRaw, String userIdRaw) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note_id must be a valid UUID");
        UUID proposalId = parseUuid(proposalIdRaw, "INVALID_CHANGE_PROPOSAL_ID", "proposal_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        log.info("action=change_proposal_apply_start user_id={} note_id={} proposal_id={}", userId, noteId, proposalId);

        ChangeProposalView proposal = changeProposalRepository.findByIdAndNoteIdAndUserId(proposalId, noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHANGE_PROPOSAL_NOT_FOUND", "change proposal not found"));
        ensurePending(proposal);

        UUID traceId = agentTraceRepository.create(
            userId,
            "CHANGE_PROPOSAL_APPLY",
            "Apply change proposal " + proposalId,
            "CHANGE_PROPOSAL",
            proposalId,
            List.of("proposal-worker"),
            Map.of("proposal_id", proposalId, "note_id", noteId, "action", "APPLY")
        );

        noteRepository.updateInterpretation(
            noteId,
            userId,
            stringValue(proposal.afterSnapshot().get("current_summary")),
            stringListValue(proposal.afterSnapshot().get("current_key_points"))
        );

        String rollbackToken = UUID.randomUUID().toString();
        changeProposalRepository.updateStatus(proposalId, ChangeProposalStatus.APPLIED, rollbackToken);
        ChangeProposalView updated = changeProposalRepository.findByIdAndUserId(proposalId, userId).orElseThrow();

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "proposal.apply",
            "COMPLETED",
            Map.of("proposal_id", proposalId, "note_id", noteId),
            Map.of("rollback_token_present", true, "status", updated.status().name()),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "CHANGE_PROPOSAL_APPLIED",
            "CHANGE_PROPOSAL",
            proposalId,
            traceId,
            Map.of("note_id", noteId, "target_layer", updated.targetLayer().name())
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Applied change proposal " + proposalId,
            Map.of("proposal_id", proposalId, "note_id", noteId, "result", "APPLIED")
        );

        log.info("action=change_proposal_apply_success user_id={} note_id={} proposal_id={} trace_id={}",
            userId, noteId, proposalId, traceId);
        return new ChangeProposalCommandResult(updated, traceId.toString());
    }

    @Transactional
    // 回滚 proposal：恢复 before snapshot 并标记回滚状态。
    public ChangeProposalCommandResult rollback(String proposalIdRaw, String userIdRaw) {
        UUID proposalId = parseUuid(proposalIdRaw, "INVALID_CHANGE_PROPOSAL_ID", "proposal_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        log.info("action=change_proposal_rollback_start user_id={} proposal_id={}", userId, proposalId);

        ChangeProposalView proposal = changeProposalRepository.findByIdAndUserId(proposalId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHANGE_PROPOSAL_NOT_FOUND", "change proposal not found"));
        ensureApplied(proposal);

        UUID traceId = agentTraceRepository.create(
            userId,
            "CHANGE_PROPOSAL_ROLLBACK",
            "Rollback change proposal " + proposalId,
            "CHANGE_PROPOSAL",
            proposalId,
            List.of("proposal-worker"),
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId(), "action", "ROLLBACK")
        );

        noteRepository.updateInterpretation(
            proposal.noteId(),
            userId,
            stringValue(proposal.beforeSnapshot().get("current_summary")),
            stringListValue(proposal.beforeSnapshot().get("current_key_points"))
        );
        changeProposalRepository.updateStatus(proposalId, ChangeProposalStatus.ROLLED_BACK, proposal.rollbackToken());
        ChangeProposalView updated = changeProposalRepository.findByIdAndUserId(proposalId, userId).orElseThrow();

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "proposal.rollback",
            "COMPLETED",
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId()),
            Map.of("status", updated.status().name(), "rollback_token_present", true),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "CHANGE_PROPOSAL_ROLLED_BACK",
            "CHANGE_PROPOSAL",
            proposalId,
            traceId,
            Map.of("note_id", proposal.noteId(), "target_layer", updated.targetLayer().name())
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Rolled back change proposal " + proposalId,
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId(), "result", "ROLLED_BACK")
        );

        log.info("action=change_proposal_rollback_success user_id={} proposal_id={} trace_id={}",
            userId, proposalId, traceId);
        return new ChangeProposalCommandResult(updated, traceId.toString());
    }

    @Transactional
    // 拒绝 proposal：不改 Note，只推进 proposal 状态并补治理记录。
    public ChangeProposalCommandResult reject(String proposalIdRaw, String userIdRaw) {
        UUID proposalId = parseUuid(proposalIdRaw, "INVALID_CHANGE_PROPOSAL_ID", "proposal_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        log.info("action=change_proposal_reject_start user_id={} proposal_id={}", userId, proposalId);

        ChangeProposalView proposal = changeProposalRepository.findByIdAndUserId(proposalId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHANGE_PROPOSAL_NOT_FOUND", "change proposal not found"));
        ensureRejectable(proposal);

        UUID traceId = agentTraceRepository.create(
            userId,
            "CHANGE_PROPOSAL_REJECT",
            "Reject change proposal " + proposalId,
            "CHANGE_PROPOSAL",
            proposalId,
            List.of("proposal-worker"),
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId(), "action", "REJECT")
        );

        changeProposalRepository.updateStatus(proposalId, ChangeProposalStatus.REJECTED, proposal.rollbackToken());
        ChangeProposalView updated = changeProposalRepository.findByIdAndUserId(proposalId, userId).orElseThrow();

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "proposal.reject",
            "COMPLETED",
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId()),
            Map.of("status", updated.status().name(), "rollback_token_present", hasText(updated.rollbackToken())),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "CHANGE_PROPOSAL_REJECTED",
            "CHANGE_PROPOSAL",
            proposalId,
            traceId,
            Map.of(
                "note_id", proposal.noteId(),
                "target_layer", updated.targetLayer().name(),
                "risk_level", updated.riskLevel().name(),
                "status", updated.status().name()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Rejected change proposal " + proposalId,
            Map.of("proposal_id", proposalId, "note_id", proposal.noteId(), "result", "REJECTED")
        );

        log.info("action=change_proposal_reject_success user_id={} proposal_id={} trace_id={}",
            userId, proposalId, traceId);
        return new ChangeProposalCommandResult(updated, traceId.toString());
    }

    private DraftInterpretation draftInterpretation(NoteQueryService.NoteDetailView note) {
        String sourceText = sourceText(note);
        List<String> keyPoints = NoteInterpretationSupport.extractKeyPoints(sourceText);
        String summary = NoteInterpretationSupport.summarize(sourceText);

        if (summary.equals(note.currentSummary()) && keyPoints.equals(note.currentKeyPoints())) {
            String refreshSeed = note.title() + ". Key points: " + String.join(" ", keyPoints);
            summary = NoteInterpretationSupport.summarize(refreshSeed);
        }
        if (summary.equals(note.currentSummary()) && keyPoints.equals(note.currentKeyPoints())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_NO_DIFF", "generated proposal does not change interpretation");
        }
        return new DraftInterpretation(summary, keyPoints);
    }

    private String sourceText(NoteQueryService.NoteDetailView note) {
        if (hasText(note.cleanText())) {
            return note.cleanText();
        }
        if (hasText(note.rawText())) {
            return note.rawText();
        }
        if (hasText(note.currentSummary())) {
            return note.currentSummary();
        }
        throw new ApiException(HttpStatus.CONFLICT, "NOTE_INTERPRETATION_SOURCE_UNAVAILABLE", "latest note content is unavailable");
    }

    private Map<String, Object> beforeSnapshot(NoteQueryService.NoteDetailView note) {
        return Map.of(
            "current_summary", note.currentSummary() == null ? "" : note.currentSummary(),
            "current_key_points", note.currentKeyPoints()
        );
    }

    private Map<String, Object> afterSnapshot(DraftInterpretation draft) {
        return Map.of(
            "current_summary", draft.summary(),
            "current_key_points", draft.keyPoints()
        );
    }

    private List<Map<String, Object>> sourceRefs(NoteQueryService.NoteDetailView note) {
        Map<String, Object> sourceRef = new LinkedHashMap<>();
        sourceRef.put("latest_content_id", note.latestContentId());
        sourceRef.put("content_type", note.latestContentType());
        if (note.sourceUri() != null) {
            sourceRef.put("source_uri", note.sourceUri());
        }
        return List.of(sourceRef);
    }

    private void ensurePending(ChangeProposalView proposal) {
        if (proposal.status() == ChangeProposalStatus.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_APPLIED", "change proposal is already applied");
        }
        if (proposal.status() == ChangeProposalStatus.ROLLED_BACK) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_ROLLED_BACK", "change proposal is already rolled back");
        }
        if (proposal.status() == ChangeProposalStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_REJECTED", "change proposal is rejected");
        }
    }

    private void ensureApplied(ChangeProposalView proposal) {
        if (proposal.status() == ChangeProposalStatus.ROLLED_BACK) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_ROLLED_BACK", "change proposal is already rolled back");
        }
        if (proposal.status() != ChangeProposalStatus.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_NOT_APPLIED", "change proposal must be applied before rollback");
        }
        if (!hasText(proposal.rollbackToken())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ROLLBACK_TOKEN_MISSING", "rollback token is missing");
        }
    }

    private void ensureRejectable(ChangeProposalView proposal) {
        if (proposal.status() == ChangeProposalStatus.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_APPLIED", "change proposal is already applied");
        }
        if (proposal.status() == ChangeProposalStatus.ROLLED_BACK) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_ROLLED_BACK", "change proposal is already rolled back");
        }
        if (proposal.status() == ChangeProposalStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_REJECTED", "change proposal is already rejected");
        }
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListValue(Object value) {
        return value == null ? List.of() : (List<String>) value;
    }

    private record DraftInterpretation(String summary, List<String> keyPoints) {
    }

    public record ChangeProposalCommandResult(ChangeProposalView proposal, String traceId) {
    }

    public record ChangeProposalView(
        UUID id,
        UUID userId,
        UUID noteId,
        UUID traceId,
        String proposalType,
        ChangeProposalTargetLayer targetLayer,
        ChangeProposalRiskLevel riskLevel,
        String diffSummary,
        Map<String, Object> beforeSnapshot,
        Map<String, Object> afterSnapshot,
        List<Map<String, Object>> sourceRefs,
        String rollbackToken,
        ChangeProposalStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
