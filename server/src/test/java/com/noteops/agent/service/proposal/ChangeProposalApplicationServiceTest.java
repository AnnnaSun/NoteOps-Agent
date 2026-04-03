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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeProposalApplicationServiceTest {

    @Test
    void generatesLowRiskInterpretationProposal() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(note(userId, noteId));

        ChangeProposalApplicationService service = newService(noteRepository, new InMemoryChangeProposalRepository());

        ChangeProposalApplicationService.ChangeProposalCommandResult result = service.generate(noteId.toString(), userId.toString());

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.proposal().targetLayer()).isEqualTo(ChangeProposalTargetLayer.INTERPRETATION);
        assertThat(result.proposal().riskLevel()).isEqualTo(ChangeProposalRiskLevel.LOW);
        assertThat(result.proposal().beforeSnapshot().get("current_summary"))
            .isNotEqualTo(result.proposal().afterSnapshot().get("current_summary"));
    }

    @Test
    void appliesProposalAndUpdatesNoteInterpretation() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(note(userId, noteId));
        InMemoryChangeProposalRepository proposalRepository = new InMemoryChangeProposalRepository();

        ChangeProposalApplicationService service = newService(noteRepository, proposalRepository);
        ChangeProposalApplicationService.ChangeProposalView proposal = service.generate(noteId.toString(), userId.toString()).proposal();

        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            service.apply(noteId.toString(), proposal.id().toString(), userId.toString());

        assertThat(result.proposal().status()).isEqualTo(ChangeProposalStatus.APPLIED);
        NoteQueryService.NoteDetailView updatedNote = noteRepository.findByIdAndUserId(noteId, userId).orElseThrow();
        assertThat(updatedNote.currentSummary()).isEqualTo(result.proposal().afterSnapshot().get("current_summary"));
        assertThat(updatedNote.currentKeyPoints()).containsExactlyElementsOf(castStringList(result.proposal().afterSnapshot().get("current_key_points")));
    }

    @Test
    void rollsBackAppliedProposalAndRestoresInterpretation() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        NoteQueryService.NoteDetailView initial = note(userId, noteId);
        noteRepository.store(initial);
        InMemoryChangeProposalRepository proposalRepository = new InMemoryChangeProposalRepository();

        ChangeProposalApplicationService service = newService(noteRepository, proposalRepository);
        ChangeProposalApplicationService.ChangeProposalView proposal = service.generate(noteId.toString(), userId.toString()).proposal();
        service.apply(noteId.toString(), proposal.id().toString(), userId.toString());

        ChangeProposalApplicationService.ChangeProposalCommandResult result =
            service.rollback(proposal.id().toString(), userId.toString());

        assertThat(result.proposal().status()).isEqualTo(ChangeProposalStatus.ROLLED_BACK);
        NoteQueryService.NoteDetailView rolledBack = noteRepository.findByIdAndUserId(noteId, userId).orElseThrow();
        assertThat(rolledBack.currentSummary()).isEqualTo(initial.currentSummary());
        assertThat(rolledBack.currentKeyPoints()).containsExactlyElementsOf(initial.currentKeyPoints());
    }

    @Test
    void rejectsApplyWhenProposalIsAlreadyApplied() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(note(userId, noteId));
        InMemoryChangeProposalRepository proposalRepository = new InMemoryChangeProposalRepository();

        ChangeProposalApplicationService service = newService(noteRepository, proposalRepository);
        ChangeProposalApplicationService.ChangeProposalView proposal = service.generate(noteId.toString(), userId.toString()).proposal();
        service.apply(noteId.toString(), proposal.id().toString(), userId.toString());

        assertThatThrownBy(() -> service.apply(noteId.toString(), proposal.id().toString(), userId.toString()))
            .isInstanceOf(ApiException.class)
            .hasMessage("change proposal is already applied");
    }

    private ChangeProposalApplicationService newService(InMemoryNoteRepository noteRepository,
                                                        InMemoryChangeProposalRepository proposalRepository) {
        return new ChangeProposalApplicationService(
            proposalRepository,
            noteRepository,
            new InMemoryAgentTraceRepository(),
            new InMemoryToolInvocationLogRepository(),
            new InMemoryUserActionEventRepository()
        );
    }

    private NoteQueryService.NoteDetailView note(UUID userId, UUID noteId) {
        String cleanText = "Kickoff covered timeline. We aligned owners. Risks were called out.";
        return new NoteQueryService.NoteDetailView(
            noteId,
            userId,
            "Kickoff",
            NoteInterpretationSupport.summarize(cleanText),
            NoteInterpretationSupport.extractKeyPoints(cleanText),
            UUID.randomUUID(),
            "CAPTURE_RAW",
            null,
            cleanText,
            cleanText,
            Instant.parse("2026-03-16T01:00:00Z"),
            Instant.parse("2026-03-16T01:00:00Z"),
            List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final Map<UUID, NoteQueryService.NoteDetailView> notes = new LinkedHashMap<>();

        void store(NoteQueryService.NoteDetailView note) {
            notes.put(note.id(), note);
        }

        @Override
        public NoteCreationResult create(UUID userId,
                                         String title,
                                         String currentSummary,
                                         List<String> currentKeyPoints,
                                         String sourceUri,
                                         String rawText,
                                         String cleanText,
                                         Map<String, Object> sourceSnapshot,
                                         Map<String, Object> analysisResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return Optional.ofNullable(notes.get(noteId))
                .filter(note -> note.userId().equals(userId));
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return notes.values().stream()
                .filter(note -> note.userId().equals(userId))
                .map(note -> new NoteQueryService.NoteSummaryView(
                    note.id(),
                    note.userId(),
                    note.title(),
                    note.currentSummary(),
                    note.currentKeyPoints(),
                    List.of(),
                    note.latestContentId(),
                    note.updatedAt()
                ))
                .sorted(Comparator.comparing(NoteQueryService.NoteSummaryView::updatedAt).reversed())
                .toList();
        }

        @Override
        public void updateInterpretation(UUID noteId, UUID userId, String currentSummary, List<String> currentKeyPoints) {
            NoteQueryService.NoteDetailView existing = findByIdAndUserId(noteId, userId).orElseThrow();
            notes.put(noteId, new NoteQueryService.NoteDetailView(
                existing.id(),
                existing.userId(),
                existing.title(),
                currentSummary,
                currentKeyPoints,
                existing.latestContentId(),
                existing.latestContentType(),
                existing.sourceUri(),
                existing.rawText(),
                existing.cleanText(),
                existing.createdAt(),
                Instant.now(),
                existing.evidenceBlocks()
            ));
        }
    }

    private static final class InMemoryChangeProposalRepository implements ChangeProposalRepository {

        private final Map<UUID, ChangeProposalApplicationService.ChangeProposalView> proposals = new LinkedHashMap<>();

        @Override
        public ChangeProposalApplicationService.ChangeProposalView create(UUID userId,
                                                                          UUID noteId,
                                                                          UUID traceId,
                                                                          String proposalType,
                                                                          ChangeProposalTargetLayer targetLayer,
                                                                          ChangeProposalRiskLevel riskLevel,
                                                                          String diffSummary,
                                                                          Map<String, Object> beforeSnapshot,
                                                                          Map<String, Object> afterSnapshot,
                                                                          List<Map<String, Object>> sourceRefs) {
            UUID id = UUID.randomUUID();
            ChangeProposalApplicationService.ChangeProposalView view = new ChangeProposalApplicationService.ChangeProposalView(
                id,
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
                Instant.now(),
                Instant.now()
            );
            proposals.put(id, view);
            return view;
        }

        @Override
        public List<ChangeProposalApplicationService.ChangeProposalView> findByNoteIdAndUserId(UUID noteId, UUID userId) {
            return proposals.values().stream()
                .filter(view -> view.noteId().equals(noteId) && view.userId().equals(userId))
                .sorted(Comparator.comparing(ChangeProposalApplicationService.ChangeProposalView::createdAt).reversed())
                .toList();
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndUserId(UUID proposalId, UUID userId) {
            return Optional.ofNullable(proposals.get(proposalId))
                .filter(view -> view.userId().equals(userId));
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndNoteIdAndUserId(UUID proposalId,
                                                                                                         UUID noteId,
                                                                                                         UUID userId) {
            return findByIdAndUserId(proposalId, userId)
                .filter(view -> view.noteId().equals(noteId));
        }

        @Override
        public void updateStatus(UUID proposalId, ChangeProposalStatus status, String rollbackToken) {
            ChangeProposalApplicationService.ChangeProposalView existing = proposals.get(proposalId);
            proposals.put(proposalId, new ChangeProposalApplicationService.ChangeProposalView(
                existing.id(),
                existing.userId(),
                existing.noteId(),
                existing.traceId(),
                existing.proposalType(),
                existing.targetLayer(),
                existing.riskLevel(),
                existing.diffSummary(),
                existing.beforeSnapshot(),
                existing.afterSnapshot(),
                existing.sourceRefs(),
                rollbackToken,
                status,
                existing.createdAt(),
                Instant.now()
            ));
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            return UUID.randomUUID();
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class InMemoryToolInvocationLogRepository implements ToolInvocationLogRepository {

        @Override
        public void append(UUID userId,
                           UUID traceId,
                           String toolName,
                           String status,
                           Map<String, Object> inputDigest,
                           Map<String, Object> outputDigest,
                           Integer latencyMs,
                           String errorCode,
                           String errorMessage) {
        }
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new ArrayList<>();

        @Override
        public void append(UUID userId,
                           String eventType,
                           String entityType,
                           UUID entityId,
                           UUID traceId,
                           Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }
}
