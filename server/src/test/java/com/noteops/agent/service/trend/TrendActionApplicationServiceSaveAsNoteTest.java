package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendActionApplicationServiceSaveAsNoteTest {

    @Test
    void forwardsSaveAsNoteToConversionServiceAndReturnsConvertedTrendItem() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        UUID convertedNoteId = UUID.randomUUID();
        UUID convertedContentId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord convertedTrendItem = trendItem(
            trendItemId,
            userId,
            TrendItemStatus.SAVED_AS_NOTE,
            convertedNoteId,
            null
        );

        RecordingTrendNoteConversionService conversionService = new RecordingTrendNoteConversionService(
            new TrendNoteConversionService.SaveAsNoteResult(
                traceId.toString(),
                new NoteRepository.NoteCreationResult(convertedNoteId, convertedContentId),
                convertedTrendItem
            ),
            null
        );
        TrendActionApplicationService service = new TrendActionApplicationService(
            new NoopTrendItemRepository(),
            conversionService,
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        TrendActionApplicationService.ActionResult result = service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "SAVE_AS_NOTE",
                "capture this as a note",
                traceId.toString()
            )
        );

        assertThat(conversionService.receivedCommands).hasSize(1);
        assertThat(conversionService.receivedCommands.getFirst().trendItemId()).isEqualTo(trendItemId);
        assertThat(conversionService.receivedCommands.getFirst().userId()).isEqualTo(userId);
        assertThat(conversionService.receivedCommands.getFirst().traceId()).isEqualTo(traceId);
        assertThat(conversionService.receivedCommands.getFirst().operatorNote()).isEqualTo("capture this as a note");
        assertThat(result.traceId()).isEqualTo(traceId.toString());
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.SAVED_AS_NOTE);
        assertThat(result.trendItem().convertedNoteId()).isEqualTo(convertedNoteId);
    }

    @Test
    void recordsFailureTraceForSaveAsNoteConversionFailures() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        ApiException failure = new ApiException(
            HttpStatus.CONFLICT,
            "TREND_NOTE_CONVERSION_FAILED",
            "trend item is not in actionable inbox state",
            traceId.toString()
        );
        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingTrendNoteConversionService conversionService = new RecordingTrendNoteConversionService(null, failure);
        TrendActionApplicationService service = new TrendActionApplicationService(
            new NoopTrendItemRepository(),
            conversionService,
            agentTraceRepository,
            toolInvocationLogRepository,
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "SAVE_AS_NOTE",
                "please convert",
                traceId.toString()
            )
        ))
            .isInstanceOf(ApiException.class)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo("TREND_NOTE_CONVERSION_FAILED");
                assertThat(exception.traceId()).isEqualTo(traceId.toString());
            });

        assertThat(agentTraceRepository.createdTraceIds).containsExactly(traceId);
        assertThat(agentTraceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolInvocationLogRepository.toolNames).containsExactly("trend.note.convert");
        assertThat(toolInvocationLogRepository.statuses).containsExactly("FAILED");
    }

    private TrendItemRepository.TrendItemRecord trendItem(UUID trendItemId,
                                                          UUID userId,
                                                          TrendItemStatus status,
                                                          UUID convertedNoteId,
                                                          UUID convertedIdeaId) {
        return new TrendItemRepository.TrendItemRecord(
            trendItemId,
            userId,
            TrendSourceType.GITHUB,
            "repo#1",
            "Trend title",
            "https://example.com",
            "Summary text",
            89.5,
            TrendAnalysisPayload.fromMap(Map.of(
                "summary", "Summary text",
                "why_it_matters", "Because it is relevant",
                "topic_tags", List.of("agents", "tooling"),
                "signal_type", "DISCUSSION",
                "note_worthy", true,
                "idea_worthy", false,
                "suggested_action", "SAVE_AS_NOTE",
                "reasoning_summary", "Strong note candidate"
            )),
            Map.of(),
            status,
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z"),
            convertedNoteId,
            convertedIdeaId,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z")
        );
    }

    private static final class RecordingTrendNoteConversionService extends TrendNoteConversionService {

        private final List<SaveAsNoteCommand> receivedCommands = new ArrayList<>();
        private final SaveAsNoteResult resultToReturn;
        private final ApiException failure;

        private RecordingTrendNoteConversionService(SaveAsNoteResult resultToReturn, ApiException failure) {
            super(
                new NoopTrendItemRepository(),
                new NoopNoteRepository(),
                new RecordingAgentTraceRepository(),
                new RecordingToolInvocationLogRepository(),
                new RecordingUserActionEventRepository(),
                transactionManager()
            );
            this.resultToReturn = resultToReturn;
            this.failure = failure;
        }

        @Override
        public SaveAsNoteResult saveAsNote(SaveAsNoteCommand command) {
            receivedCommands.add(command);
            if (failure != null) {
                throw failure;
            }
            return resultToReturn;
        }
    }

    private static final class NoopTrendItemRepository implements TrendItemRepository {

        @Override
        public TrendItemRecord create(UUID userId,
                                      TrendSourceType sourceType,
                                      String sourceItemKey,
                                      String title,
                                      String url,
                                      String summary,
                                      double normalizedScore,
                                      TrendAnalysisPayload analysisPayload,
                                      Map<String, Object> extraAttributes,
                                      TrendItemStatus status,
                                      TrendActionType suggestedAction,
                                      Instant sourcePublishedAt,
                                      Instant lastIngestedAt,
                                      UUID convertedNoteId,
                                      UUID convertedIdeaId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TrendItemRecord> findByIdAndUserId(UUID trendItemId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public Optional<TrendItemRecord> findBySourceKey(UUID userId, TrendSourceType sourceType, String sourceItemKey) {
            return Optional.empty();
        }

        @Override
        public List<TrendItemRecord> findInboxByUserId(UUID userId, TrendItemStatus status, TrendSourceType sourceType) {
            return List.of();
        }

        @Override
        public TrendItemIngestResult upsertIngested(UUID userId,
                                                    TrendSourceType sourceType,
                                                    String sourceItemKey,
                                                    String title,
                                                    String url,
                                                    double normalizedScore,
                                                    Map<String, Object> extraAttributes,
                                                    Instant sourcePublishedAt,
                                                    Instant lastIngestedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrendItemRecord> findAllByUserId(UUID userId) {
            return List.of();
        }

        @Override
        public TrendItemRecord updateStatus(UUID trendItemId,
                                            UUID userId,
                                            TrendItemStatus status,
                                            TrendActionType suggestedAction,
                                            UUID convertedNoteId,
                                            UUID convertedIdeaId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TrendItemRecord updateAnalysis(UUID trendItemId, UUID userId, TrendAnalysisPayload analysisPayload) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoopNoteRepository implements NoteRepository {

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
        public Optional<com.noteops.agent.service.note.NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<com.noteops.agent.service.note.NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return List.of();
        }
    }

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final List<UUID> createdTraceIds = new ArrayList<>();
        private final List<UUID> completedTraceIds = new ArrayList<>();
        private final List<UUID> failedTraceIds = new ArrayList<>();

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            UUID traceId = UUID.randomUUID();
            createdTraceIds.add(traceId);
            return traceId;
        }

        @Override
        public UUID create(UUID traceId,
                           UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            createdTraceIds.add(traceId);
            return traceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completedTraceIds.add(traceId);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            failedTraceIds.add(traceId);
        }
    }

    private static final class RecordingToolInvocationLogRepository implements ToolInvocationLogRepository {

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

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }

    private static PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
