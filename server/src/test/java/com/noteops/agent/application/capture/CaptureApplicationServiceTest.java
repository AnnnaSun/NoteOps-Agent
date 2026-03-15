package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureInputType;
import com.noteops.agent.domain.capture.CaptureJobStatus;
import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.persistence.capture.CaptureJobRepository;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureApplicationServiceTest {

    @Test
    void createsTextCaptureAndPersistsGovernanceRecords() {
        InMemoryCaptureJobRepository captureJobRepository = new InMemoryCaptureJobRepository();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        InMemoryAgentTraceRepository agentTraceRepository = new InMemoryAgentTraceRepository();
        InMemoryToolInvocationLogRepository toolInvocationLogRepository = new InMemoryToolInvocationLogRepository();
        InMemoryUserActionEventRepository userActionEventRepository = new InMemoryUserActionEventRepository();

        CaptureApplicationService service = new CaptureApplicationService(
            captureJobRepository,
            noteRepository,
            agentTraceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        UUID userId = UUID.randomUUID();
        CaptureApplicationService.CaptureView captureView = service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                userId.toString(),
                "TEXT",
                "First line.\nSecond line with detail.",
                null
            )
        );

        assertThat(captureView.status()).isEqualTo(CaptureJobStatus.COMPLETED);
        assertThat(captureView.noteId()).isNotBlank();
        assertThat(captureJobRepository.lastCapture.extractedPayload.get("clean_text"))
            .isEqualTo("First line.\nSecond line with detail.");
        assertThat(agentTraceRepository.completedTraces).hasSize(1);
        assertThat(toolInvocationLogRepository.toolNames)
            .containsExactly("capture.text-input-normalizer", "capture.summary-analyzer", "capture.note-consolidator");
        assertThat(userActionEventRepository.eventTypes)
            .containsExactly("CAPTURE_SUBMITTED", "NOTE_CREATED_FROM_CAPTURE");
        assertThat(noteRepository.lastTitle).contains("First line.");
    }

    @Test
    void createsUrlCaptureWithPlaceholderExtraction() {
        InMemoryCaptureJobRepository captureJobRepository = new InMemoryCaptureJobRepository();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        CaptureApplicationService service = new CaptureApplicationService(
            captureJobRepository,
            noteRepository,
            new InMemoryAgentTraceRepository(),
            new InMemoryToolInvocationLogRepository(),
            new InMemoryUserActionEventRepository()
        );

        UUID userId = UUID.randomUUID();
        CaptureApplicationService.CaptureView captureView = service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                userId.toString(),
                "URL",
                null,
                "https://example.com/article"
            )
        );

        assertThat(captureView.status()).isEqualTo(CaptureJobStatus.COMPLETED);
        assertThat(noteRepository.lastSourceUri).isEqualTo("https://example.com/article");
        assertThat(captureJobRepository.lastCapture.extractedPayload.get("extraction_mode"))
            .isEqualTo("URL_PLACEHOLDER");
    }

    private static final class InMemoryCaptureJobRepository implements CaptureJobRepository {

        private final Map<UUID, StoredCapture> captures = new HashMap<>();
        private StoredCapture lastCapture;

        @Override
        public void createReceived(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput) {
            lastCapture = new StoredCapture(id, userId, inputType, sourceUri, rawInput);
            captures.put(id, lastCapture);
        }

        @Override
        public void updateExtraction(UUID captureId, Map<String, Object> extractedPayload) {
            StoredCapture capture = captures.get(captureId);
            capture.status = CaptureJobStatus.EXTRACTING;
            capture.extractedPayload = extractedPayload;
        }

        @Override
        public void updateAnalysis(UUID captureId, Map<String, Object> analysisResult) {
            StoredCapture capture = captures.get(captureId);
            capture.status = CaptureJobStatus.ANALYZING;
            capture.analysisResult = analysisResult;
        }

        @Override
        public void updateConsolidation(UUID captureId, Map<String, Object> consolidationResult) {
            StoredCapture capture = captures.get(captureId);
            capture.status = CaptureJobStatus.CONSOLIDATING;
            capture.consolidationResult = consolidationResult;
        }

        @Override
        public void markCompleted(UUID captureId) {
            captures.get(captureId).status = CaptureJobStatus.COMPLETED;
        }

        @Override
        public void markFailed(UUID captureId, String errorCode, String errorMessage) {
            StoredCapture capture = captures.get(captureId);
            capture.status = CaptureJobStatus.FAILED;
            capture.errorCode = errorCode;
            capture.errorMessage = errorMessage;
        }

        @Override
        public Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId) {
            StoredCapture capture = captures.get(captureId);
            if (capture == null || !capture.userId.equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(new CaptureApplicationService.CaptureView(
                capture.id,
                capture.userId,
                capture.inputType,
                capture.sourceUri,
                capture.rawInput,
                capture.status,
                capture.errorCode,
                capture.errorMessage,
                capture.createdAt,
                capture.updatedAt,
                stringValue(capture.consolidationResult.get("note_id")),
                stringValue(capture.consolidationResult.get("trace_id"))
            ));
        }

        private String stringValue(Object value) {
            return value == null ? null : value.toString();
        }
    }

    private static final class StoredCapture {
        private final UUID id;
        private final UUID userId;
        private final CaptureInputType inputType;
        private final String sourceUri;
        private final String rawInput;
        private final Instant createdAt = Instant.now();
        private Instant updatedAt = createdAt;
        private CaptureJobStatus status = CaptureJobStatus.RECEIVED;
        private Map<String, Object> extractedPayload = Map.of();
        private Map<String, Object> analysisResult = Map.of();
        private Map<String, Object> consolidationResult = Map.of();
        private String errorCode;
        private String errorMessage;

        private StoredCapture(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput) {
            this.id = id;
            this.userId = userId;
            this.inputType = inputType;
            this.sourceUri = sourceUri;
            this.rawInput = rawInput;
        }
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private String lastTitle;
        private String lastSourceUri;
        private NoteQueryService.NoteDetailView storedNote;

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
            UUID noteId = UUID.randomUUID();
            UUID contentId = UUID.randomUUID();
            lastTitle = title;
            lastSourceUri = sourceUri;
            storedNote = new NoteQueryService.NoteDetailView(
                noteId,
                userId,
                title,
                currentSummary,
                currentKeyPoints,
                contentId,
                "CAPTURE_RAW",
                sourceUri,
                rawText,
                cleanText,
                Instant.now(),
                Instant.now()
            );
            return new NoteCreationResult(noteId, contentId);
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return Optional.ofNullable(storedNote)
                .filter(note -> note.id().equals(noteId) && note.userId().equals(userId));
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return storedNote == null || !storedNote.userId().equals(userId)
                ? List.of()
                : List.of(new NoteQueryService.NoteSummaryView(
                    storedNote.id(),
                    storedNote.userId(),
                    storedNote.title(),
                    storedNote.currentSummary(),
                    storedNote.currentKeyPoints(),
                    storedNote.latestContentId(),
                    storedNote.updatedAt()
                ));
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        private final List<UUID> completedTraces = new java.util.ArrayList<>();
        private UUID latestTraceId;

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            latestTraceId = UUID.randomUUID();
            return latestTraceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completedTraces.add(traceId);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class InMemoryToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final List<String> toolNames = new java.util.ArrayList<>();

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
        }
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new java.util.ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }
}
