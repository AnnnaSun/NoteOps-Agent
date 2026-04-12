package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.note.NoteQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeaApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-07T12:00:00Z");

    @Test
    void createsManualIdeaAndWritesTraceAndEvent() {
        UUID userId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        IdeaApplicationService service = newService(
            ideaRepository,
            new InMemoryNoteRepository(),
            traceRepository,
            eventRepository
        );

        IdeaApplicationService.IdeaCommandResult result = service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "MANUAL",
                null,
                "Manual idea",
                "Validate this path"
            )
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.idea().sourceMode()).isEqualTo(IdeaSourceMode.MANUAL);
        assertThat(result.idea().sourceNoteId()).isNull();
        assertThat(result.idea().status()).isEqualTo(IdeaStatus.CAPTURED);
        assertThat(result.idea().assessmentResult()).isEqualTo(IdeaAssessmentResult.empty());
        assertThat(traceRepository.entryTypes).containsExactly("IDEA_CREATE");
        assertThat(eventRepository.eventTypes).containsExactly("IDEA_CREATED");
    }

    @Test
    void createsIdeaFromNoteAndUsesSourceNoteBinding() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(userId, noteId, "Source note");
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        IdeaApplicationService service = newService(
            new InMemoryIdeaRepository(),
            noteRepository,
            new InMemoryAgentTraceRepository(),
            eventRepository
        );

        IdeaApplicationService.IdeaCommandResult result = service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "FROM_NOTE",
                noteId.toString(),
                "Idea from note",
                "Promote this note"
            )
        );

        assertThat(result.idea().sourceMode()).isEqualTo(IdeaSourceMode.FROM_NOTE);
        assertThat(result.idea().sourceNoteId()).isEqualTo(noteId);
        assertThat(eventRepository.eventTypes).containsExactly("IDEA_DERIVED_FROM_NOTE");
    }

    @Test
    void rejectsMissingSourceNoteWhenSourceModeIsFromNote() {
        UUID userId = UUID.randomUUID();
        IdeaApplicationService service = newService(
            new InMemoryIdeaRepository(),
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "FROM_NOTE",
                null,
                "Bad idea",
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("source_note_id is required when source_mode is FROM_NOTE");
    }

    @Test
    void rejectsUnexpectedSourceNoteForManualIdea() {
        UUID userId = UUID.randomUUID();
        IdeaApplicationService service = newService(
            new InMemoryIdeaRepository(),
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "MANUAL",
                UUID.randomUUID().toString(),
                "Bad idea",
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("source_note_id must be empty when source_mode is MANUAL");
    }

    @Test
    void rejectsUnknownSourceNoteOwnership() {
        UUID userId = UUID.randomUUID();
        IdeaApplicationService service = newService(
            new InMemoryIdeaRepository(),
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "FROM_NOTE",
                UUID.randomUUID().toString(),
                "Missing note",
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("note not found");
    }

    @Test
    void rejectsFromTrendSourceModeFromPublicCreatePath() {
        UUID userId = UUID.randomUUID();
        IdeaApplicationService service = newService(
            new InMemoryIdeaRepository(),
            new InMemoryNoteRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.create(
            new IdeaApplicationService.CreateIdeaCommand(
                userId.toString(),
                "FROM_TREND",
                null,
                "Bad idea",
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("source_mode FROM_TREND is internal-only");
    }

    private IdeaApplicationService newService(IdeaRepository ideaRepository,
                                              NoteRepository noteRepository,
                                              AgentTraceRepository traceRepository,
                                              UserActionEventRepository eventRepository) {
        return new IdeaApplicationService(
            ideaRepository,
            noteRepository,
            traceRepository,
            eventRepository
        );
    }

    private static final class InMemoryIdeaRepository implements IdeaRepository {

        private final Map<UUID, IdeaRecord> ideas = new HashMap<>();

        @Override
        public IdeaRecord create(UUID userId,
                                 IdeaSourceMode sourceMode,
                                 UUID sourceNoteId,
                                 UUID sourceTrendItemId,
                                 String title,
                                 String rawDescription,
                                 IdeaStatus status,
                                 IdeaAssessmentResult assessmentResult) {
            UUID ideaId = UUID.randomUUID();
            IdeaRecord record = new IdeaRecord(
                ideaId,
                userId,
                sourceMode,
                sourceNoteId,
                sourceTrendItemId,
                title,
                rawDescription,
                status,
                assessmentResult == null ? IdeaAssessmentResult.empty() : assessmentResult,
                NOW,
                NOW
            );
            ideas.put(ideaId, record);
            return record;
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
            throw new UnsupportedOperationException();
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

        void store(UUID userId, UUID noteId, String title) {
            notes.put(noteId, new NoteQueryService.NoteDetailView(
                noteId,
                userId,
                title,
                "summary",
                List.of("point"),
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
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
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
}
