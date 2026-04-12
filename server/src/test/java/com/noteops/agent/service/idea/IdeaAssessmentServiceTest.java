package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.service.note.NoteQueryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeaAssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-08T12:00:00Z");

    @Test
    void assessesCapturedIdeaAndWritesTraceEventAndToolLog() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.FROM_NOTE,
            noteId,
            "Idea from note",
            "Build a note-to-action workflow",
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty(),
            NOW,
            NOW
        ));

        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(userId, noteId, "Source note", "Source summary", List.of("Point A", "Point B"));
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        InMemoryToolInvocationLogRepository toolRepository = new InMemoryToolInvocationLogRepository();
        StubIdeaAgent ideaAgent = new StubIdeaAgent();
        IdeaAssessmentService service = new IdeaAssessmentService(
            ideaRepository,
            noteRepository,
            traceRepository,
            eventRepository,
            toolRepository,
            ideaAgent,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        IdeaAssessmentService.IdeaAssessmentCommandResult result = service.assess(
            new IdeaAssessmentService.AssessIdeaCommand(ideaId.toString(), userId.toString())
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.idea().status()).isEqualTo(IdeaStatus.ASSESSED);
        assertThat(result.idea().assessmentResult().problemStatement()).isEqualTo("Teams lose momentum after capturing insights");
        assertThat(result.idea().assessmentResult().targetUser()).isEqualTo("Knowledge workers with many fragmented notes");
        assertThat(traceRepository.entryTypes).containsExactly("IDEA_ASSESS");
        assertThat(eventRepository.eventTypes).containsExactly("IDEA_ASSESSED");
        assertThat(toolRepository.toolNames).containsExactly("idea.assess");
        assertThat(toolRepository.statuses).containsExactly("COMPLETED");
        assertThat(ideaAgent.lastRequest.ideaId()).isEqualTo(ideaId);
        assertThat(ideaAgent.lastRequest.noteSummary()).isEqualTo("Source summary");
    }

    @Test
    void rejectsMissingIdea() {
        IdeaAssessmentService service = new IdeaAssessmentService(
            new InMemoryIdeaRepository(),
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            new InMemoryToolInvocationLogRepository(),
            new StubIdeaAgent(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.assess(
            new IdeaAssessmentService.AssessIdeaCommand(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("idea not found");
    }

    @Test
    void rejectsIdeaThatIsNotCaptured() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Already assessed idea",
            null,
            IdeaStatus.ASSESSED,
            new IdeaAssessmentResult(
                "Existing problem",
                "Existing target",
                "Existing hypothesis",
                List.of("Existing path"),
                List.of("Existing action"),
                List.of(),
                "Existing reasoning"
            ),
            NOW,
            NOW
        ));

        IdeaAssessmentService service = new IdeaAssessmentService(
            ideaRepository,
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            new InMemoryToolInvocationLogRepository(),
            new StubIdeaAgent(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.assess(
            new IdeaAssessmentService.AssessIdeaCommand(ideaId.toString(), userId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("only CAPTURED ideas can be assessed");
    }

    private static final class StubIdeaAgent implements IdeaAgent {

        private AssessIdeaRequest lastRequest;

        @Override
        public IdeaAssessmentResult assess(AssessIdeaRequest request) {
            lastRequest = request;
            return new IdeaAssessmentResult(
                "Teams lose momentum after capturing insights",
                "Knowledge workers with many fragmented notes",
                "Structured follow-up turns notes into execution",
                List.of("Interview 3 active note takers", "Prototype a minimal loop"),
                List.of("Draft assessment summary", "Validate with one real workflow"),
                List.of("May overlap with task management tools"),
                "The source note suggests an execution gap rather than a storage gap"
            );
        }
    }

    private static final class InMemoryIdeaRepository implements IdeaRepository {

        private final Map<UUID, IdeaRecord> ideas = new HashMap<>();

        void store(IdeaRecord record) {
            ideas.put(record.id(), record);
        }

        @Override
        public IdeaRecord create(UUID userId,
                                 IdeaSourceMode sourceMode,
                                 UUID sourceNoteId,
                                 UUID sourceTrendItemId,
                                 String title,
                                 String rawDescription,
                                 IdeaStatus status,
                                 IdeaAssessmentResult assessmentResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
            IdeaRecord record = ideas.get(ideaId);
            if (record == null || !record.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public List<IdeaRecord> findAllByUserId(UUID userId) {
            return ideas.values().stream()
                .filter(record -> record.userId().equals(userId))
                .toList();
        }

        @Override
        public IdeaRecord updateAssessment(UUID ideaId, UUID userId, IdeaAssessmentResult assessmentResult, IdeaStatus status) {
            IdeaRecord current = findByIdAndUserId(ideaId, userId).orElseThrow();
            IdeaRecord updated = new IdeaRecord(
                current.id(),
                current.userId(),
                current.sourceMode(),
                current.sourceNoteId(),
                current.sourceTrendItemId(),
                current.title(),
                current.rawDescription(),
                status,
                assessmentResult,
                current.createdAt(),
                NOW
            );
            ideas.put(ideaId, updated);
            return updated;
        }

        @Override
        public IdeaRecord updateStatus(UUID ideaId, UUID userId, IdeaStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdeaRecord> updateStatusIfCurrent(UUID ideaId, UUID userId, IdeaStatus currentStatus, IdeaStatus targetStatus) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final Map<UUID, NoteQueryService.NoteDetailView> notes = new HashMap<>();

        void store(UUID userId, UUID noteId, String title, String summary, List<String> keyPoints) {
            notes.put(noteId, new NoteQueryService.NoteDetailView(
                noteId,
                userId,
                title,
                summary,
                keyPoints,
                UUID.randomUUID(),
                "CAPTURE_RAW",
                null,
                null,
                null,
                NOW,
                NOW,
                List.of()
            ));
        }

        @Override
        public NoteCreationResult create(UUID userId, String title, String currentSummary, List<String> currentKeyPoints, String sourceUri, String rawText, String cleanText, Map<String, Object> sourceSnapshot, Map<String, Object> analysisResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            NoteQueryService.NoteDetailView note = notes.get(noteId);
            if (note == null || !note.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(note);
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return List.of();
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        private final List<String> entryTypes = new ArrayList<>();

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            entryTypes.add(entryType);
            return UUID.randomUUID();
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }

    private static final class InMemoryToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final List<String> toolNames = new ArrayList<>();
        private final List<String> statuses = new ArrayList<>();

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
            toolNames.add(toolName);
            statuses.add(status);
        }
    }
}
