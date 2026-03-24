package com.noteops.agent.service.search;

import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.proposal.ChangeProposalRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchGovernanceApplicationServiceTest {

    @Test
    void savesEvidenceWithoutOverwritingCurrentInterpretation() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository(userId, noteId);
        RecordingChangeProposalRepository proposalRepository = new RecordingChangeProposalRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();

        SearchGovernanceApplicationService service = new SearchGovernanceApplicationService(
            noteRepository,
            proposalRepository,
            traceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        SearchGovernanceApplicationService.SearchEvidenceCommandResult result = service.saveEvidence(
            noteId.toString(),
            new SearchGovernanceApplicationService.SaveSearchEvidenceCommand(
                userId.toString(),
                "kickoff alpha",
                "Search Stub Background",
                "stub://search/background?q=kickoff+alpha",
                "Background reading related to kickoff alpha",
                List.of("kickoff", "alpha"),
                "背景补充",
                List.of("BACKGROUND"),
                "背景资料聚焦 kickoff alpha"
            )
        );

        assertThat(result.evidence().noteId()).isEqualTo(noteId);
        assertThat(noteRepository.appendedContentType).isEqualTo("EVIDENCE");
        assertThat(noteRepository.currentSummary).isEqualTo("Current summary");
        assertThat(noteRepository.updateInterpretationCalls).isZero();
        assertThat(userActionEventRepository.events).extracting(UserActionEventRecord::eventType)
            .containsExactly("SEARCH_EVIDENCE_SAVED");
        assertThat(toolInvocationLogRepository.logs.getFirst().toolName()).isEqualTo("search.evidence.save");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("content_type", "EVIDENCE");
    }

    @Test
    void generatesTraceableProposalFromSearchSupplement() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository(userId, noteId);
        RecordingChangeProposalRepository proposalRepository = new RecordingChangeProposalRepository();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();

        SearchGovernanceApplicationService service = new SearchGovernanceApplicationService(
            noteRepository,
            proposalRepository,
            traceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        ChangeProposalApplicationService.ChangeProposalCommandResult result = service.generateProposal(
            noteId.toString(),
            new SearchGovernanceApplicationService.GenerateSearchProposalCommand(
                userId.toString(),
                "kickoff alpha",
                "Search Stub Background",
                "stub://search/background?q=kickoff+alpha",
                "Background reading related to kickoff alpha",
                List.of("kickoff", "alpha"),
                "可能更新",
                List.of("BACKGROUND"),
                "外部线索提示 kickoff 计划有新增进展"
            )
        );

        assertThat(result.proposal().proposalType()).isEqualTo("SEARCH_EVIDENCE_REFRESH_INTERPRETATION");
        assertThat(result.proposal().targetLayer()).isEqualTo(ChangeProposalTargetLayer.INTERPRETATION);
        assertThat(result.proposal().afterSnapshot()).containsKey("current_summary");
        assertThat(result.proposal().sourceRefs().getFirst()).containsEntry("source_uri", "stub://search/background?q=kickoff+alpha");
        assertThat(userActionEventRepository.events).extracting(UserActionEventRecord::eventType)
            .containsExactly("SEARCH_PROPOSAL_CREATED");
        assertThat(toolInvocationLogRepository.logs.getFirst().toolName()).isEqualTo("search.proposal.generate");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("result", "CREATED");
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final UUID userId;
        private final UUID noteId;
        private String currentSummary = "Current summary";
        private List<String> currentKeyPoints = List.of("point-1");
        private String appendedContentType;
        private int updateInterpretationCalls;

        private InMemoryNoteRepository(UUID userId, UUID noteId) {
            this.userId = userId;
            this.noteId = noteId;
        }

        @Override
        public NoteCreationResult create(UUID userId, String title, String currentSummary, List<String> currentKeyPoints, String sourceUri, String rawText, String cleanText, Map<String, Object> sourceSnapshot, Map<String, Object> analysisResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            if (!this.noteId.equals(noteId) || !this.userId.equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(new NoteQueryService.NoteDetailView(
                noteId,
                userId,
                "Kickoff note",
                currentSummary,
                currentKeyPoints,
                UUID.randomUUID(),
                "CAPTURE_RAW",
                "stub://note/source",
                "raw",
                "clean",
                Instant.parse("2026-03-20T01:00:00Z"),
                Instant.parse("2026-03-20T01:00:00Z")
            ));
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return List.of();
        }

        @Override
        public void updateInterpretation(UUID noteId, UUID userId, String currentSummary, List<String> currentKeyPoints) {
            updateInterpretationCalls++;
            this.currentSummary = currentSummary;
            this.currentKeyPoints = currentKeyPoints;
        }

        @Override
        public UUID appendEvidence(UUID noteId, UUID userId, String sourceUri, String rawText, String cleanText, Map<String, Object> sourceSnapshot, Map<String, Object> analysisResult) {
            appendedContentType = "EVIDENCE";
            return UUID.randomUUID();
        }
    }

    private static final class RecordingChangeProposalRepository implements ChangeProposalRepository {

        private ChangeProposalApplicationService.ChangeProposalView created;

        @Override
        public ChangeProposalApplicationService.ChangeProposalView create(UUID userId, UUID noteId, UUID traceId, String proposalType, ChangeProposalTargetLayer targetLayer, ChangeProposalRiskLevel riskLevel, String diffSummary, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot, List<Map<String, Object>> sourceRefs) {
            created = new ChangeProposalApplicationService.ChangeProposalView(
                UUID.randomUUID(),
                userId,
                noteId,
                traceId,
                proposalType,
                targetLayer,
                riskLevel,
                diffSummary,
                beforeSnapshot,
                afterSnapshot,
                sourceRefs,
                null,
                ChangeProposalStatus.PENDING_REVIEW,
                Instant.parse("2026-03-20T01:00:00Z"),
                Instant.parse("2026-03-20T01:00:00Z")
            );
            return created;
        }

        @Override
        public List<ChangeProposalApplicationService.ChangeProposalView> findByNoteIdAndUserId(UUID noteId, UUID userId) {
            return List.of();
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndUserId(UUID proposalId, UUID userId) {
            return Optional.ofNullable(created);
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndNoteIdAndUserId(UUID proposalId, UUID noteId, UUID userId) {
            return Optional.ofNullable(created);
        }

        @Override
        public void updateStatus(UUID proposalId, ChangeProposalStatus status, String rollbackToken) {
        }
    }

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final UUID traceId = UUID.randomUUID();
        private TraceCreateRecord created;
        private TraceCompletedRecord completed;

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            created = new TraceCreateRecord(userId, entryType, goal, rootEntityType, rootEntityId, workerSequence, orchestratorState);
            return traceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completed = new TraceCompletedRecord(traceId, resultSummary, orchestratorState);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {

        private final List<UserActionEventRecord> events = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            events.add(new UserActionEventRecord(userId, eventType, entityType, entityId, traceId, payload));
        }
    }

    private static final class RecordingToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final List<ToolInvocationLogRecord> logs = new ArrayList<>();

        @Override
        public void append(UUID userId, UUID traceId, String toolName, String status, Map<String, Object> inputDigest, Map<String, Object> outputDigest, Integer latencyMs, String errorCode, String errorMessage) {
            logs.add(new ToolInvocationLogRecord(userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage));
        }
    }

    private record TraceCreateRecord(
        UUID userId,
        String entryType,
        String goal,
        String rootEntityType,
        UUID rootEntityId,
        List<String> workerSequence,
        Map<String, Object> orchestratorState
    ) {
    }

    private record TraceCompletedRecord(
        UUID traceId,
        String resultSummary,
        Map<String, Object> orchestratorState
    ) {
    }

    private record UserActionEventRecord(
        UUID userId,
        String eventType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload
    ) {
    }

    private record ToolInvocationLogRecord(
        UUID userId,
        UUID traceId,
        String toolName,
        String status,
        Map<String, Object> inputDigest,
        Map<String, Object> outputDigest,
        Integer latencyMs,
        String errorCode,
        String errorMessage
    ) {
    }
}
