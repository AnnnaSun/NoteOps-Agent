package com.noteops.agent.service.search;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.proposal.ChangeProposalRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.service.note.NoteInterpretationSupport;
import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchGovernanceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SearchGovernanceApplicationService.class);
    private static final String SEARCH_PROPOSAL_TYPE = "SEARCH_EVIDENCE_REFRESH_INTERPRETATION";

    private final NoteRepository noteRepository;
    private final ChangeProposalRepository changeProposalRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    public SearchGovernanceApplicationService(NoteRepository noteRepository,
                                              ChangeProposalRepository changeProposalRepository,
                                              AgentTraceRepository agentTraceRepository,
                                              ToolInvocationLogRepository toolInvocationLogRepository,
                                              UserActionEventRepository userActionEventRepository) {
        this.noteRepository = noteRepository;
        this.changeProposalRepository = changeProposalRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    @Transactional
    // 将外部补充保存为 EVIDENCE block，只追加 note_contents，不更新 Note 当前解释层。
    public SearchEvidenceCommandResult saveEvidence(String noteIdRaw, SaveSearchEvidenceCommand command) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        SearchSupplement supplement = SearchSupplement.from(command);
        NoteQueryService.NoteDetailView note = noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));

        log.info(
            "module=SearchGovernanceApplicationService action=search_evidence_save_start path=/api/v1/search/notes/{noteId}/evidence user_id={} note_id={} source_uri={}",
            userId,
            noteId,
            supplement.sourceUri()
        );

        UUID traceId = agentTraceRepository.create(
            userId,
            "SEARCH_EVIDENCE_SAVE",
            "Save search supplement as evidence for note " + noteId,
            "NOTE",
            noteId,
            List.of("search-governance-worker"),
            Map.of(
                "note_id", noteId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri(),
                "relation_label", supplement.relationLabel()
            )
        );

        UUID contentId = noteRepository.appendEvidence(
            noteId,
            userId,
            supplement.sourceUri(),
            evidenceRawText(supplement),
            supplement.summarySnippet(),
            supplement.sourceSnapshot(),
            supplement.analysisResult()
        );

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "search.evidence.save",
            "COMPLETED",
            Map.of(
                "note_id", noteId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri()
            ),
            Map.of(
                "content_id", contentId,
                "content_type", "EVIDENCE"
            ),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "SEARCH_EVIDENCE_SAVED",
            "NOTE",
            noteId,
            traceId,
            Map.of(
                "content_id", contentId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri(),
                "relation_label", supplement.relationLabel()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Saved search evidence " + contentId + " for note " + noteId,
            Map.of(
                "note_id", noteId,
                "content_id", contentId,
                "content_type", "EVIDENCE",
                "note_current_summary", note.currentSummary()
            )
        );
        log.info(
            "module=SearchGovernanceApplicationService action=search_evidence_save_success path=/api/v1/search/notes/{noteId}/evidence user_id={} note_id={} content_id={} trace_id={}",
            userId,
            noteId,
            contentId,
            traceId
        );

        return new SearchEvidenceCommandResult(
            new SearchEvidenceSaveView(noteId, contentId, "EVIDENCE", supplement.sourceUri(), supplement.relationLabel()),
            traceId.toString()
        );
    }

    @Transactional
    // 基于外部补充生成 proposal 候选，保持解释层更新仍走治理链路。
    public ChangeProposalApplicationService.ChangeProposalCommandResult generateProposal(String noteIdRaw,
                                                                                         GenerateSearchProposalCommand command) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        SearchSupplement supplement = SearchSupplement.from(command);
        NoteQueryService.NoteDetailView note = noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));

        log.info(
            "module=SearchGovernanceApplicationService action=search_proposal_generate_start path=/api/v1/search/notes/{noteId}/change-proposals user_id={} note_id={} source_uri={}",
            userId,
            noteId,
            supplement.sourceUri()
        );

        UUID traceId = agentTraceRepository.create(
            userId,
            "SEARCH_PROPOSAL_GENERATE",
            "Generate search-backed proposal for note " + noteId,
            "NOTE",
            noteId,
            List.of("search-governance-worker"),
            Map.of(
                "note_id", noteId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri(),
                "relation_label", supplement.relationLabel()
            )
        );

        DraftInterpretation draft = draftInterpretation(note, supplement);
        ChangeProposalApplicationService.ChangeProposalView proposal = changeProposalRepository.create(
            userId,
            noteId,
            traceId,
            SEARCH_PROPOSAL_TYPE,
            ChangeProposalTargetLayer.INTERPRETATION,
            riskLevel(supplement.relationLabel()),
            diffSummary(supplement),
            beforeSnapshot(note),
            afterSnapshot(draft),
            List.of(supplement.sourceSnapshot())
        );

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "search.proposal.generate",
            "COMPLETED",
            Map.of(
                "note_id", noteId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri()
            ),
            Map.of(
                "proposal_id", proposal.id(),
                "risk_level", proposal.riskLevel().name()
            ),
            1,
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "SEARCH_PROPOSAL_CREATED",
            "CHANGE_PROPOSAL",
            proposal.id(),
            traceId,
            Map.of(
                "note_id", noteId,
                "query", supplement.query(),
                "source_uri", supplement.sourceUri(),
                "relation_label", supplement.relationLabel()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Generated search proposal " + proposal.id(),
            Map.of(
                "note_id", noteId,
                "proposal_id", proposal.id(),
                "result", "CREATED"
            )
        );
        log.info(
            "module=SearchGovernanceApplicationService action=search_proposal_generate_success path=/api/v1/search/notes/{noteId}/change-proposals user_id={} note_id={} proposal_id={} trace_id={}",
            userId,
            noteId,
            proposal.id(),
            traceId
        );

        return new ChangeProposalApplicationService.ChangeProposalCommandResult(proposal, traceId.toString());
    }

    private DraftInterpretation draftInterpretation(NoteQueryService.NoteDetailView note, SearchSupplement supplement) {
        String noteSummary = note.currentSummary() == null ? "" : note.currentSummary().trim();
        String supplementText = supplement.summarySnippet();
        String candidateSummary = NoteInterpretationSupport.summarize(
            (noteSummary + "\n" + supplement.relationLabel() + ": " + supplementText).trim()
        );

        LinkedHashSet<String> keyPoints = new LinkedHashSet<>(note.currentKeyPoints() == null ? List.of() : note.currentKeyPoints());
        keyPoints.add(supplement.relationLabel() + "：" + supplementText);
        for (String keyword : supplement.keywords()) {
            keyPoints.add("关键词：" + keyword);
        }
        List<String> candidateKeyPoints = keyPoints.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(4)
            .toList();

        if (candidateSummary.equals(note.currentSummary()) && candidateKeyPoints.equals(note.currentKeyPoints())) {
            throw new ApiException(HttpStatus.CONFLICT, "SEARCH_PROPOSAL_NO_DIFF", "generated proposal does not change interpretation");
        }
        return new DraftInterpretation(candidateSummary, candidateKeyPoints);
    }

    private Map<String, Object> beforeSnapshot(NoteQueryService.NoteDetailView note) {
        return Map.of(
            "current_summary", note.currentSummary() == null ? "" : note.currentSummary(),
            "current_key_points", note.currentKeyPoints() == null ? List.of() : note.currentKeyPoints()
        );
    }

    private Map<String, Object> afterSnapshot(DraftInterpretation draft) {
        return Map.of(
            "current_summary", draft.summary(),
            "current_key_points", draft.keyPoints()
        );
    }

    private String diffSummary(SearchSupplement supplement) {
        return supplement.relationLabel() + "：根据外部补充刷新 current_summary 与 current_key_points。";
    }

    private ChangeProposalRiskLevel riskLevel(String relationLabel) {
        return "可能冲突".equals(relationLabel) ? ChangeProposalRiskLevel.MEDIUM : ChangeProposalRiskLevel.LOW;
    }

    private String evidenceRawText(SearchSupplement supplement) {
        List<String> lines = new ArrayList<>();
        lines.add("query: " + supplement.query());
        lines.add("source_name: " + supplement.sourceName());
        lines.add("source_uri: " + supplement.sourceUri());
        lines.add("relation_label: " + supplement.relationLabel());
        if (!supplement.keywords().isEmpty()) {
            lines.add("keywords: " + String.join(", ", supplement.keywords()));
        }
        lines.add("summary: " + supplement.summary());
        if (!supplement.summarySnippet().equals(supplement.summary())) {
            lines.add("summary_snippet: " + supplement.summarySnippet());
        }
        return String.join("\n", lines);
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private record DraftInterpretation(String summary, List<String> keyPoints) {
    }

    public record SaveSearchEvidenceCommand(
        String userId,
        String query,
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        String relationLabel,
        List<String> relationTags,
        String summarySnippet
    ) {
    }

    public record GenerateSearchProposalCommand(
        String userId,
        String query,
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        String relationLabel,
        List<String> relationTags,
        String summarySnippet
    ) {
    }

    public record SearchEvidenceSaveView(
        UUID noteId,
        UUID contentId,
        String contentType,
        String sourceUri,
        String relationLabel
    ) {
    }

    public record SearchEvidenceCommandResult(
        SearchEvidenceSaveView evidence,
        String traceId
    ) {
    }

    private record SearchSupplement(
        String query,
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        String relationLabel,
        List<String> relationTags,
        String summarySnippet
    ) {
        static SearchSupplement from(SaveSearchEvidenceCommand command) {
            return from(
                command.query(),
                command.sourceName(),
                command.sourceUri(),
                command.summary(),
                command.keywords(),
                command.relationLabel(),
                command.relationTags(),
                command.summarySnippet()
            );
        }

        static SearchSupplement from(GenerateSearchProposalCommand command) {
            return from(
                command.query(),
                command.sourceName(),
                command.sourceUri(),
                command.summary(),
                command.keywords(),
                command.relationLabel(),
                command.relationTags(),
                command.summarySnippet()
            );
        }

        private static SearchSupplement from(String query,
                                             String sourceName,
                                             String sourceUri,
                                             String summary,
                                             List<String> keywords,
                                             String relationLabel,
                                             List<String> relationTags,
                                             String summarySnippet) {
            String normalizedSourceUri = requireText(sourceUri, "INVALID_SEARCH_SUPPLEMENT_SOURCE_URI", "source_uri must not be blank");
            String normalizedSourceName = requireText(sourceName, "INVALID_SEARCH_SUPPLEMENT_SOURCE_NAME", "source_name must not be blank");
            String normalizedQuery = requireText(query, "INVALID_SEARCH_SUPPLEMENT_QUERY", "query must not be blank");
            String normalizedSummary = requireText(summary, "INVALID_SEARCH_SUPPLEMENT_SUMMARY", "summary must not be blank");
            String normalizedRelationLabel = normalizeRelationLabel(relationLabel);
            List<String> normalizedKeywords = normalizeKeywords(keywords);
            String normalizedSnippet = summarySnippet == null || summarySnippet.isBlank() ? normalizedSummary : summarySnippet.trim();
            List<String> normalizedRelationTags = relationTags == null ? List.of() : relationTags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
            return new SearchSupplement(
                normalizedQuery,
                normalizedSourceName,
                normalizedSourceUri,
                normalizedSummary,
                normalizedKeywords,
                normalizedRelationLabel,
                normalizedRelationTags,
                normalizedSnippet
            );
        }

        Map<String, Object> sourceSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("query", query);
            snapshot.put("source_name", sourceName);
            snapshot.put("source_uri", sourceUri);
            snapshot.put("summary", summary);
            snapshot.put("summary_snippet", summarySnippet);
            snapshot.put("keywords", keywords);
            snapshot.put("relation_label", relationLabel);
            snapshot.put("relation_tags", relationTags);
            return snapshot;
        }

        Map<String, Object> analysisResult() {
            return Map.of(
                "query", query,
                "keywords", keywords,
                "relation_label", relationLabel,
                "summary_snippet", summarySnippet
            );
        }

        private static String requireText(String value, String errorCode, String message) {
            if (value == null || value.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
            }
            return value.trim();
        }

        private static String normalizeRelationLabel(String value) {
            String normalized = requireText(value, "INVALID_SEARCH_SUPPLEMENT_RELATION_LABEL", "relation_label must not be blank");
            if (!List.of("可能更新", "可能冲突", "背景补充", "延伸阅读").contains(normalized)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_SUPPLEMENT_RELATION_LABEL", "relation_label is invalid");
            }
            return normalized;
        }

        private static List<String> normalizeKeywords(List<String> keywords) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (keywords != null) {
                keywords.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .limit(4)
                    .forEach(normalized::add);
            }
            if (normalized.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_SUPPLEMENT_KEYWORDS", "keywords must not be empty");
            }
            return normalized.stream().toList();
        }
    }
}
