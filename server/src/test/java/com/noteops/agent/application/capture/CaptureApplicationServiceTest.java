package com.noteops.agent.application.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.api.ApiException;
import com.noteops.agent.application.ai.AiProperties;
import com.noteops.agent.application.ai.AiProvider;
import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.domain.capture.CaptureAnalysisResult;
import com.noteops.agent.domain.capture.CaptureFailureReason;
import com.noteops.agent.domain.capture.CaptureInputType;
import com.noteops.agent.domain.capture.CaptureJobStatus;
import com.noteops.agent.persistence.capture.CaptureJobRepository;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureApplicationServiceTest {

    @Test
    void completesTextCaptureAndPersistsGovernanceRecords() {
        TestFixture fixture = new TestFixture();

        CaptureApplicationService.CaptureView captureView = fixture.service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                fixture.userId.toString(),
                "TEXT",
                " First line.\nSecond line with detail. ",
                null,
                "Title hint"
            )
        );

        assertThat(captureView.status()).isEqualTo(CaptureJobStatus.COMPLETED);
        assertThat(captureView.sourceType()).isEqualTo(CaptureInputType.TEXT);
        assertThat(captureView.noteId()).isNotNull();
        assertThat(captureView.failureReason()).isNull();
        assertThat(captureView.analysisPreview().titleCandidate()).isEqualTo("Captured title");
        assertThat(fixture.captureJobRepository.transitions(captureView.captureJobId()))
            .containsExactly(
                CaptureJobStatus.RECEIVED,
                CaptureJobStatus.EXTRACTING,
                CaptureJobStatus.ANALYZING,
                CaptureJobStatus.CONSOLIDATING,
                CaptureJobStatus.COMPLETED
            );
        assertThat(fixture.captureJobRepository.capture(captureView.captureJobId()).analysisResult.get("summary"))
            .isEqualTo("Structured summary");
        assertThat(fixture.noteRepository.lastCurrentTags).containsExactly("capture", "ai");
        assertThat(fixture.toolInvocationLogRepository.toolNames)
            .containsExactly("capture.text-input-normalizer", "capture.analysis.fake", "capture.note-consolidator");
        assertThat(fixture.userActionEventRepository.eventTypes)
            .containsExactly("CAPTURE_SUBMITTED", "NOTE_CREATED_FROM_CAPTURE");
        assertThat(fixture.agentTraceRepository.completedStates).hasSize(1);
        assertThat(fixture.agentTraceRepository.completedStates.get(0))
            .containsEntry("result", "COMPLETED")
            .containsKey("result_note_id");
    }

    @Test
    void marksCaptureFailedWhenUrlExtractionFails() {
        TestFixture fixture = new TestFixture();
        fixture.extractor.failure = new CapturePipelineException(CaptureFailureReason.EXTRACTION_FAILED, "url extraction failed");

        assertThatThrownBy(() -> fixture.service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                fixture.userId.toString(),
                "URL",
                null,
                "https://example.com/article",
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).errorCode()).isEqualTo("EXTRACTION_FAILED"));

        StoredCapture capture = fixture.captureJobRepository.lastCapture();
        CaptureApplicationService.CaptureView failedView = fixture.service.get(capture.id.toString(), fixture.userId.toString());
        assertThat(failedView.status()).isEqualTo(CaptureJobStatus.FAILED);
        assertThat(failedView.failureReason()).isEqualTo(CaptureFailureReason.EXTRACTION_FAILED);
        assertThat(fixture.captureJobRepository.transitions(capture.id))
            .containsExactly(CaptureJobStatus.RECEIVED, CaptureJobStatus.EXTRACTING, CaptureJobStatus.FAILED);
    }

    @Test
    void marksCaptureFailedWhenLlmCallFails() {
        TestFixture fixture = new TestFixture();
        fixture.analysisClient.failure = new CapturePipelineException(CaptureFailureReason.LLM_CALL_FAILED, "provider unavailable");

        assertThatThrownBy(() -> fixture.service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                fixture.userId.toString(),
                "TEXT",
                "Need analysis",
                null,
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).errorCode()).isEqualTo("LLM_CALL_FAILED"));

        StoredCapture capture = fixture.captureJobRepository.lastCapture();
        CaptureApplicationService.CaptureView failedView = fixture.service.get(capture.id.toString(), fixture.userId.toString());
        assertThat(failedView.status()).isEqualTo(CaptureJobStatus.FAILED);
        assertThat(failedView.failureReason()).isEqualTo(CaptureFailureReason.LLM_CALL_FAILED);
        assertThat(fixture.captureJobRepository.transitions(capture.id))
            .containsExactly(CaptureJobStatus.RECEIVED, CaptureJobStatus.EXTRACTING, CaptureJobStatus.ANALYZING, CaptureJobStatus.FAILED);
        assertThat(fixture.toolInvocationLogRepository.statuses).contains("FAILED");
    }

    @Test
    void marksCaptureFailedWhenLlmOutputIsInvalid() {
        TestFixture fixture = new TestFixture();
        fixture.analysisClient.rawJson = """
            {
              "title_candidate": "  ",
              "summary": "",
              "key_points": [],
              "tags": [],
              "idea_candidate": null,
              "confidence": 1.5,
              "language": null,
              "warnings": []
            }
            """;

        assertThatThrownBy(() -> fixture.service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                fixture.userId.toString(),
                "TEXT",
                "Need validation",
                null,
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).errorCode()).isEqualTo("LLM_OUTPUT_INVALID"));

        StoredCapture capture = fixture.captureJobRepository.lastCapture();
        CaptureApplicationService.CaptureView failedView = fixture.service.get(capture.id.toString(), fixture.userId.toString());
        assertThat(failedView.status()).isEqualTo(CaptureJobStatus.FAILED);
        assertThat(failedView.failureReason()).isEqualTo(CaptureFailureReason.LLM_OUTPUT_INVALID);
        assertThat(fixture.captureJobRepository.transitions(capture.id))
            .containsExactly(CaptureJobStatus.RECEIVED, CaptureJobStatus.EXTRACTING, CaptureJobStatus.ANALYZING, CaptureJobStatus.FAILED);
    }

    @Test
    void marksCaptureFailedWhenConsolidationFails() {
        TestFixture fixture = new TestFixture();
        fixture.noteRepository.failWith = new RuntimeException("note insert failed");

        assertThatThrownBy(() -> fixture.service.create(
            new CaptureApplicationService.CreateCaptureCommand(
                fixture.userId.toString(),
                "TEXT",
                "Need note creation",
                null,
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).errorCode()).isEqualTo("CONSOLIDATION_FAILED"));

        StoredCapture capture = fixture.captureJobRepository.lastCapture();
        CaptureApplicationService.CaptureView failedView = fixture.service.get(capture.id.toString(), fixture.userId.toString());
        assertThat(failedView.status()).isEqualTo(CaptureJobStatus.FAILED);
        assertThat(failedView.failureReason()).isEqualTo(CaptureFailureReason.CONSOLIDATION_FAILED);
        assertThat(fixture.captureJobRepository.transitions(capture.id))
            .containsExactly(
                CaptureJobStatus.RECEIVED,
                CaptureJobStatus.EXTRACTING,
                CaptureJobStatus.ANALYZING,
                CaptureJobStatus.CONSOLIDATING,
                CaptureJobStatus.FAILED
            );
    }

    private static final class TestFixture {
        private final UUID userId = UUID.randomUUID();
        private final InMemoryCaptureJobRepository captureJobRepository = new InMemoryCaptureJobRepository();
        private final InMemoryAgentTraceRepository agentTraceRepository = new InMemoryAgentTraceRepository();
        private final InMemoryToolInvocationLogRepository toolInvocationLogRepository = new InMemoryToolInvocationLogRepository();
        private final InMemoryUserActionEventRepository userActionEventRepository = new InMemoryUserActionEventRepository();
        private final ConfigurableCaptureExtractor extractor = new ConfigurableCaptureExtractor();
        private final ConfigurableAnalysisClient analysisClient = new ConfigurableAnalysisClient(toolInvocationLogRepository);
        private final InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        private final CaptureApplicationService service;

        private TestFixture() {
            AiProperties properties = new AiProperties(
                AiProvider.DEEPSEEK,
                Duration.ofSeconds(20),
                Map.of(CaptureAnalysisClient.ROUTE_KEY, new AiProperties.Route(AiProvider.DEEPSEEK, null)),
                new AiProperties.DeepSeek("https://api.deepseek.com", "test-key", "deepseek-chat"),
                new AiProperties.Kimi("https://api.moonshot.cn/v1", "kimi-test-key", "kimi-test-model"),
                new AiProperties.Gemini("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-test-key", "gemini-test-model"),
                new AiProperties.Ollama("http://localhost:11434", "llama-test")
            );
            CaptureAnalysisWorker analysisWorker = new CaptureAnalysisWorker(analysisClient, new ObjectMapper());
            CaptureNoteConsolidator consolidator = new CaptureNoteConsolidator(noteRepository);
            CaptureOrchestrator orchestrator = new CaptureOrchestrator(
                captureJobRepository,
                extractor,
                analysisWorker,
                consolidator,
                properties,
                agentTraceRepository,
                toolInvocationLogRepository,
                userActionEventRepository
            );
            service = new CaptureApplicationService(captureJobRepository, orchestrator);
        }
    }

    private static final class InMemoryCaptureJobRepository implements CaptureJobRepository {

        private final Map<UUID, StoredCapture> captures = new HashMap<>();
        private StoredCapture lastCapture;

        @Override
        public void createReceived(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput) {
            StoredCapture capture = new StoredCapture(id, userId, inputType, sourceUri, rawInput);
            capture.transition(CaptureJobStatus.RECEIVED);
            captures.put(id, capture);
            lastCapture = capture;
        }

        @Override
        public void markExtracting(UUID captureId) {
            captures.get(captureId).transition(CaptureJobStatus.EXTRACTING);
        }

        @Override
        public void saveExtractionResult(UUID captureId, Map<String, Object> extractedPayload) {
            captures.get(captureId).extractedPayload = extractedPayload;
        }

        @Override
        public void markAnalyzing(UUID captureId) {
            captures.get(captureId).transition(CaptureJobStatus.ANALYZING);
        }

        @Override
        public void saveAnalysisResult(UUID captureId, CaptureAnalysisResult analysisResult) {
            captures.get(captureId).analysisResult = analysisResult == null ? Map.of() : analysisResult.toMap();
        }

        @Override
        public void markConsolidating(UUID captureId) {
            captures.get(captureId).transition(CaptureJobStatus.CONSOLIDATING);
        }

        @Override
        public void saveConsolidationResult(UUID captureId, Map<String, Object> consolidationResult) {
            captures.get(captureId).consolidationResult = consolidationResult;
        }

        @Override
        public void markCompleted(UUID captureId) {
            captures.get(captureId).transition(CaptureJobStatus.COMPLETED);
        }

        @Override
        public void markFailed(UUID captureId, CaptureFailureReason failureReason, String errorMessage) {
            StoredCapture capture = captures.get(captureId);
            capture.failureReason = failureReason;
            capture.errorMessage = errorMessage;
            capture.transition(CaptureJobStatus.FAILED);
        }

        @Override
        public Optional<CaptureApplicationService.CaptureView> findByIdAndUserId(UUID captureId, UUID userId) {
            StoredCapture capture = captures.get(captureId);
            if (capture == null || !capture.userId.equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(new CaptureApplicationService.CaptureView(
                capture.id,
                capture.inputType,
                capture.status,
                uuidValue(capture.consolidationResult.get("note_id")),
                capture.failureReason,
                CaptureAnalysisResult.fromMap(capture.analysisResult),
                capture.createdAt,
                capture.updatedAt,
                capture.traceId
            ));
        }

        private List<CaptureJobStatus> transitions(UUID captureId) {
            return List.copyOf(captures.get(captureId).transitionHistory);
        }

        private StoredCapture capture(UUID captureId) {
            return captures.get(captureId);
        }

        private StoredCapture lastCapture() {
            return lastCapture;
        }

        private UUID uuidValue(Object value) {
            return value == null ? null : UUID.fromString(value.toString());
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
        private CaptureJobStatus status;
        private CaptureFailureReason failureReason;
        private String errorMessage;
        private UUID traceId;
        private Map<String, Object> extractedPayload = Map.of();
        private Map<String, Object> analysisResult = Map.of();
        private Map<String, Object> consolidationResult = Map.of();
        private final List<CaptureJobStatus> transitionHistory = new ArrayList<>();

        private StoredCapture(UUID id, UUID userId, CaptureInputType inputType, String sourceUri, String rawInput) {
            this.id = id;
            this.userId = userId;
            this.inputType = inputType;
            this.sourceUri = sourceUri;
            this.rawInput = rawInput;
        }

        private void transition(CaptureJobStatus nextStatus) {
            status = nextStatus;
            updatedAt = Instant.now();
            transitionHistory.add(nextStatus);
        }
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private List<String> lastCurrentTags = List.of();
        private RuntimeException failWith;

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
            return create(
                userId,
                title,
                currentSummary,
                currentKeyPoints,
                List.of(),
                sourceUri,
                rawText,
                cleanText,
                sourceSnapshot,
                analysisResult
            );
        }

        @Override
        public NoteCreationResult create(UUID userId,
                                         String title,
                                         String currentSummary,
                                         List<String> currentKeyPoints,
                                         List<String> currentTags,
                                         String sourceUri,
                                         String rawText,
                                         String cleanText,
                                         Map<String, Object> sourceSnapshot,
                                         Map<String, Object> analysisResult) {
            if (failWith != null) {
                throw failWith;
            }
            lastCurrentTags = currentTags;
            return new NoteCreationResult(UUID.randomUUID(), UUID.randomUUID());
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return List.of();
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        private final List<Map<String, Object>> completedStates = new ArrayList<>();
        private final List<Map<String, Object>> failedStates = new ArrayList<>();

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
            completedStates.add(orchestratorState);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            failedStates.add(orchestratorState);
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

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }

    private static final class ConfigurableCaptureExtractor implements CaptureExtractor {

        private CapturePipelineException failure;

        @Override
        public ExtractionResult extract(ExtractionCommand command) {
            if (failure != null) {
                throw failure;
            }
            String rawText = command.sourceType() == CaptureInputType.TEXT
                ? command.rawText()
                : "Example page title\n\nExample url snapshot";
            String cleanText = command.sourceType() == CaptureInputType.TEXT
                ? "First line. Second line with detail."
                : "Example url snapshot";
            return new ExtractionResult(
                rawText,
                cleanText,
                command.sourceUrl(),
                command.sourceType() == CaptureInputType.TEXT ? "INLINE_TEXT" : "HTTP_URL_SNAPSHOT",
                command.sourceType() == CaptureInputType.URL ? "Example page title" : command.titleHint()
            );
        }
    }

    private static final class ConfigurableAnalysisClient implements CaptureAnalysisClient {

        private final ToolInvocationLogRepository toolInvocationLogRepository;
        private CapturePipelineException failure;
        private String rawJson = """
            {
              "title_candidate": "Captured title",
              "summary": "Structured summary",
              "key_points": ["point-1", "point-2"],
              "tags": ["capture", "ai"],
              "idea_candidate": null,
              "confidence": 0.82,
              "language": "en",
              "warnings": []
            }
            """;

        private ConfigurableAnalysisClient(ToolInvocationLogRepository toolInvocationLogRepository) {
            this.toolInvocationLogRepository = toolInvocationLogRepository;
        }

        @Override
        public AnalyzeResponse analyze(AnalyzeRequest request) {
            if (failure != null) {
                toolInvocationLogRepository.append(
                    request.userId(),
                    request.traceId(),
                    "capture.analysis.fake",
                    "FAILED",
                    Map.of("request_type", "CAPTURE_ANALYSIS"),
                    Map.of("result", "FAILED"),
                    7,
                    failure.failureReason().name(),
                    failure.getMessage()
                );
                throw failure;
            }
            toolInvocationLogRepository.append(
                request.userId(),
                request.traceId(),
                "capture.analysis.fake",
                "SUCCESS",
                Map.of("request_type", "CAPTURE_ANALYSIS"),
                Map.of("result", "SUCCESS"),
                7,
                null,
                null
            );
            return new AnalyzeResponse(AiProvider.DEEPSEEK, "fake-model", rawJson, 7);
        }
    }
}
