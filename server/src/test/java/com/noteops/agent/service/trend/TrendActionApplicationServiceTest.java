package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendActionApplicationServiceTest {

    @Test
    void ignoresTrendItemAndAppendsGovernanceRecords() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID trendItemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID traceId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        TrendItemRepository.TrendItemRecord analyzed = trendItemRepository.store(
            new TrendItemRepository.TrendItemRecord(
                trendItemId,
                userId,
                TrendSourceType.GITHUB,
                "repo#123",
                "OpenAI adds workflow primitives",
                "https://example.com",
                "Summary",
                0.92,
                TrendAnalysisPayload.fromMap(Map.of(
                    "summary", "Summary",
                    "why_it_matters", "Affects workflows",
                    "topic_tags", List.of("agent"),
                    "signal_type", "PRODUCT",
                    "note_worthy", true,
                    "idea_worthy", false,
                    "suggested_action", "SAVE_AS_NOTE",
                    "reasoning_summary", "Useful"
                )),
                Map.of(),
                TrendItemStatus.ANALYZED,
                TrendActionType.SAVE_AS_NOTE,
                Instant.parse("2026-04-11T10:00:00Z"),
                Instant.parse("2026-04-11T10:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-11T10:00:00Z"),
                Instant.parse("2026-04-11T10:00:00Z")
            )
        );
        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();
        TrendActionApplicationService service = new TrendActionApplicationService(
            trendItemRepository,
            agentTraceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        TrendActionApplicationService.ActionResult result = service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "IGNORE",
                "too noisy",
                traceId.toString()
            )
        );

        assertThat(result.traceId()).isEqualTo(traceId.toString());
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.IGNORED);
        assertThat(result.trendItem().suggestedAction()).isEqualTo(TrendActionType.SAVE_AS_NOTE);
        assertThat(trendItemRepository.findByIdAndUserId(trendItemId, userId)).get()
            .extracting(TrendItemRepository.TrendItemRecord::status)
            .isEqualTo(TrendItemStatus.IGNORED);
        assertThat(trendItemRepository.findByIdAndUserId(trendItemId, userId)).get()
            .extracting(TrendItemRepository.TrendItemRecord::suggestedAction)
            .isEqualTo(TrendActionType.SAVE_AS_NOTE);
        assertThat(agentTraceRepository.createdTraceIds).containsExactly(traceId);
        assertThat(agentTraceRepository.completedTraceIds).containsExactly(traceId);
        assertThat(toolInvocationLogRepository.toolNames).containsExactly("trend.item.action", "trend.item.action");
        assertThat(toolInvocationLogRepository.statuses).containsExactly("STARTED", "COMPLETED");
        assertThat(userActionEventRepository.eventTypes).containsExactly("TREND_ITEM_IGNORED");
        assertThat(userActionEventRepository.payloads.get(0)).containsEntry("action", "IGNORE");
        assertThat(userActionEventRepository.payloads.get(0)).containsEntry("status", "IGNORED");
        assertThat(userActionEventRepository.payloads.get(0)).containsEntry("suggested_action", "SAVE_AS_NOTE");
    }

    @Test
    void forwardsPromoteToIdeaToConversionServiceAndReturnsConvertedTrendItem() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        UUID convertedIdeaId = UUID.randomUUID();
        TrendItemRepository.TrendItemRecord convertedTrendItem = promotableTrendItem(
            userId,
            trendItemId,
            convertedIdeaId
        );

        RecordingTrendIdeaConversionService conversionService = new RecordingTrendIdeaConversionService(
            new TrendIdeaConversionService.PromoteToIdeaResult(
                traceId.toString(),
                ideaRecord(userId, trendItemId, convertedIdeaId),
                convertedTrendItem
            ),
            null
        );
        TrendActionApplicationService service = new TrendActionApplicationService(
            new InMemoryTrendItemRepository(),
            null,
            conversionService,
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        TrendActionApplicationService.ActionResult result = service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "PROMOTE_TO_IDEA",
                "turn this into an idea",
                traceId.toString()
            )
        );

        assertThat(conversionService.receivedCommands).hasSize(1);
        assertThat(conversionService.receivedCommands.getFirst().trendItemId()).isEqualTo(trendItemId);
        assertThat(conversionService.receivedCommands.getFirst().userId()).isEqualTo(userId);
        assertThat(conversionService.receivedCommands.getFirst().traceId()).isEqualTo(traceId);
        assertThat(conversionService.receivedCommands.getFirst().operatorNote()).isEqualTo("turn this into an idea");
        assertThat(result.traceId()).isEqualTo(traceId.toString());
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.PROMOTED_TO_IDEA);
        assertThat(result.trendItem().convertedIdeaId()).isEqualTo(convertedIdeaId);
    }

    @Test
    void promotesTrendItemToIdeaAndDelegatesToConversionService() {
        UUID userId = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
        UUID trendItemId = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");
        UUID traceId = UUID.fromString("cccccccc-3333-3333-3333-333333333333");
        UUID convertedIdeaId = UUID.fromString("dddddddd-4444-4444-4444-444444444444");

        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        trendItemRepository.store(actionableTrendItem(userId, trendItemId));

        AtomicReference<TrendIdeaConversionService.PromoteToIdeaCommand> received = new AtomicReference<>();
        TrendIdeaConversionService trendIdeaConversionService = mock(TrendIdeaConversionService.class);
        when(trendIdeaConversionService.promoteToIdea(any())).thenAnswer(invocation -> {
            TrendIdeaConversionService.PromoteToIdeaCommand command = invocation.getArgument(0);
            received.set(command);
            TrendItemRepository.TrendItemRecord updated = trendItemRepository.updateStatus(
                command.trendItemId(),
                command.userId(),
                TrendItemStatus.PROMOTED_TO_IDEA,
                TrendActionType.PROMOTE_TO_IDEA,
                null,
                convertedIdeaId
            );
            return new TrendIdeaConversionService.PromoteToIdeaResult(
                command.traceId().toString(),
                null,
                updated
            );
        });

        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();
        TrendActionApplicationService service = new TrendActionApplicationService(
            trendItemRepository,
            null,
            trendIdeaConversionService,
            agentTraceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        TrendActionApplicationService.ActionResult result = service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "PROMOTE_TO_IDEA",
                "promote it",
                traceId.toString()
            )
        );

        assertThat(received.get()).isNotNull();
        assertThat(received.get().trendItemId()).isEqualTo(trendItemId);
        assertThat(received.get().userId()).isEqualTo(userId);
        assertThat(received.get().traceId()).isEqualTo(traceId);
        assertThat(received.get().operatorNote()).isEqualTo("promote it");

        assertThat(result.traceId()).isEqualTo(traceId.toString());
        assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.PROMOTED_TO_IDEA);
        assertThat(result.trendItem().convertedIdeaId()).isEqualTo(convertedIdeaId);
        assertThat(trendItemRepository.findByIdAndUserId(trendItemId, userId)).get()
            .extracting(TrendItemRepository.TrendItemRecord::convertedIdeaId)
            .isEqualTo(convertedIdeaId);

        assertThat(agentTraceRepository.createdTraceIds).isEmpty();
        assertThat(toolInvocationLogRepository.toolNames).isEmpty();
        assertThat(userActionEventRepository.eventTypes).isEmpty();
    }

    @Test
    void rejectsInvalidUserIdWithRequestTraceId() {
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        TrendActionApplicationService service = new TrendActionApplicationService(
            new InMemoryTrendItemRepository(),
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                "bad-user-id",
                "IGNORE",
                null,
                traceId.toString()
            )
        ))
            .isInstanceOf(ApiException.class)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo("INVALID_USER_ID");
                assertThat(exception.traceId()).isEqualTo(traceId.toString());
            });
    }

    @Test
    void rejectsInvalidTrendItemIdWithRequestTraceId() {
        UUID userId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        TrendActionApplicationService service = new TrendActionApplicationService(
            new InMemoryTrendItemRepository(),
            agentTraceRepository,
            toolInvocationLogRepository,
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                "bad-trend-id",
                userId.toString(),
                "IGNORE",
                null,
                traceId.toString()
            )
        ))
            .isInstanceOf(ApiException.class)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo("INVALID_TREND_ITEM_ID");
                assertThat(exception.traceId()).isEqualTo(traceId.toString());
            });
        assertThat(agentTraceRepository.createdTraceIds).isEmpty();
        assertThat(agentTraceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolInvocationLogRepository.toolNames).containsExactly("trend.item.action");
        assertThat(toolInvocationLogRepository.statuses).containsExactly("FAILED");
    }

    @Test
    void writesFailureLogForDeferredAction() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        trendItemRepository.store(actionableTrendItem(userId, trendItemId));
        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        TrendActionApplicationService service = new TrendActionApplicationService(
            trendItemRepository,
            agentTraceRepository,
            toolInvocationLogRepository,
            new RecordingUserActionEventRepository()
        );
        Logger logger = (Logger) LoggerFactory.getLogger(TrendActionApplicationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.act(
                new TrendActionApplicationService.ActionCommand(
                    trendItemId.toString(),
                    userId.toString(),
                    "SAVE_AS_NOTE",
                    "deferred",
                    traceId.toString()
                )
            ))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("TREND_NOTE_CONVERSION_FAILED");
                    assertThat(exception.traceId()).isEqualTo(traceId.toString());
                });

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("trend_note_convert_fail");
                assertThat(event.getFormattedMessage()).contains("trace_id=" + traceId);
                assertThat(event.getFormattedMessage()).contains("error_code=TREND_NOTE_CONVERSION_FAILED");
            });
            assertThat(agentTraceRepository.createdTraceIds).containsExactly(traceId);
            assertThat(agentTraceRepository.failedTraceIds).containsExactly(traceId);
            assertThat(toolInvocationLogRepository.toolNames).containsExactly("trend.note.convert");
            assertThat(toolInvocationLogRepository.statuses).containsExactly("FAILED");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void rejectsDeferredActionsWithControlledError() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        trendItemRepository.store(actionableTrendItem(userId, trendItemId));

        TrendActionApplicationService service = new TrendActionApplicationService(
            trendItemRepository,
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "SAVE_AS_NOTE",
                null,
                traceId.toString()
            )
        ))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo("TREND_NOTE_CONVERSION_FAILED");
                assertThat(exception.traceId()).isEqualTo(traceId.toString());
            });
    }

    @Test
    void rejectsMissingTrendItem() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        TrendActionApplicationService service = new TrendActionApplicationService(
            new InMemoryTrendItemRepository(),
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "IGNORE",
                null,
                traceId.toString()
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("trend item not found");
    }

    @Test
    void rejectsNonActionableTrendItemAndWritesFailureTrace() {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        trendItemRepository.store(new TrendItemRepository.TrendItemRecord(
            trendItemId,
            userId,
            TrendSourceType.HN,
            "hn-999",
            "Trend title",
            "https://example.com",
            "Summary",
            13.0,
            TrendAnalysisPayload.empty(),
            Map.of(),
            TrendItemStatus.INGESTED,
            null,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z"),
            null,
            null,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z")
        ));
        RecordingAgentTraceRepository agentTraceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        TrendActionApplicationService service = new TrendActionApplicationService(
            trendItemRepository,
            agentTraceRepository,
            toolInvocationLogRepository,
            new RecordingUserActionEventRepository()
        );

        assertThatThrownBy(() -> service.act(
            new TrendActionApplicationService.ActionCommand(
                trendItemId.toString(),
                userId.toString(),
                "IGNORE",
                null,
                traceId.toString()
            )
        ))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo("TREND_ITEM_NOT_ACTIONABLE");
                assertThat(exception.traceId()).isEqualTo(traceId.toString());
            });

        assertThat(agentTraceRepository.createdTraceIds).containsExactly(traceId);
        assertThat(agentTraceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolInvocationLogRepository.toolNames).containsExactly("trend.item.action", "trend.item.action");
        assertThat(toolInvocationLogRepository.statuses).containsExactly("STARTED", "FAILED");
    }

    private TrendItemRepository.TrendItemRecord actionableTrendItem(UUID userId, UUID trendItemId) {
        return new TrendItemRepository.TrendItemRecord(
            trendItemId,
            userId,
            TrendSourceType.HN,
            "hn-123",
            "Trend title",
            "https://example.com",
            "Summary",
            77.0,
            TrendAnalysisPayload.fromMap(Map.of(
                "summary", "Summary",
                "why_it_matters", "Useful",
                "topic_tags", List.of("trend"),
                "signal_type", "DISCUSSION",
                "note_worthy", true,
                "idea_worthy", false,
                "suggested_action", "IGNORE",
                "reasoning_summary", "Helpful"
            )),
            Map.of(),
            TrendItemStatus.ANALYZED,
            TrendActionType.IGNORE,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z"),
            null,
            null,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z")
        );
    }

    private TrendItemRepository.TrendItemRecord promotableTrendItem(UUID userId,
                                                                    UUID trendItemId,
                                                                    UUID convertedIdeaId) {
        return new TrendItemRepository.TrendItemRecord(
            trendItemId,
            userId,
            TrendSourceType.HN,
            "hn-idea-123",
            "Idea-worthy trend title",
            "https://example.com/idea",
            "Summary",
            79.0,
            TrendAnalysisPayload.fromMap(Map.of(
                "summary", "Summary",
                "why_it_matters", "Worth elevating",
                "topic_tags", List.of("trend", "idea"),
                "signal_type", "DISCUSSION",
                "note_worthy", false,
                "idea_worthy", true,
                "suggested_action", "PROMOTE_TO_IDEA",
                "reasoning_summary", "Strong idea candidate"
            )),
            Map.of(),
            TrendItemStatus.PROMOTED_TO_IDEA,
            TrendActionType.PROMOTE_TO_IDEA,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z"),
            null,
            convertedIdeaId,
            Instant.parse("2026-04-11T09:00:00Z"),
            Instant.parse("2026-04-11T09:05:00Z")
        );
    }

    private IdeaRepository.IdeaRecord ideaRecord(UUID userId, UUID trendItemId, UUID ideaId) {
        return new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.FROM_TREND,
            null,
            trendItemId,
            "Idea-worthy trend title",
            "Source: https://example.com/idea\n\nTrend summary:\nSummary\n\nAnalysis summary:\nSummary",
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty(),
            Instant.parse("2026-04-11T09:10:00Z"),
            Instant.parse("2026-04-11T09:10:00Z")
        );
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

    private static final class InMemoryTrendItemRepository implements TrendItemRepository {

        private final Map<UUID, TrendItemRecord> items = new HashMap<>();

        TrendItemRecord store(TrendItemRecord record) {
            items.put(record.id(), record);
            return record;
        }

        @Override
        public TrendItemRecord create(UUID userId, TrendSourceType sourceType, String sourceItemKey, String title, String url, String summary, double normalizedScore, TrendAnalysisPayload analysisPayload, Map<String, Object> extraAttributes, TrendItemStatus status, TrendActionType suggestedAction, Instant sourcePublishedAt, Instant lastIngestedAt, UUID convertedNoteId, UUID convertedIdeaId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TrendItemRecord> findByIdAndUserId(UUID trendItemId, UUID userId) {
            TrendItemRecord record = items.get(trendItemId);
            if (record == null || !record.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(record);
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
        public TrendItemIngestResult upsertIngested(UUID userId, TrendSourceType sourceType, String sourceItemKey, String title, String url, double normalizedScore, Map<String, Object> extraAttributes, Instant sourcePublishedAt, Instant lastIngestedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrendItemRecord> findAllByUserId(UUID userId) {
            return items.values().stream().filter(record -> record.userId().equals(userId)).toList();
        }

        @Override
        public TrendItemRecord updateStatus(UUID trendItemId, UUID userId, TrendItemStatus status, TrendActionType suggestedAction, UUID convertedNoteId, UUID convertedIdeaId) {
            TrendItemRecord current = items.get(trendItemId);
            TrendItemRecord updated = new TrendItemRecord(
                current.id(),
                current.userId(),
                current.sourceType(),
                current.sourceItemKey(),
                current.title(),
                current.url(),
                current.summary(),
                current.normalizedScore(),
                current.analysisPayload(),
                current.extraAttributes(),
                status,
                suggestedAction,
                current.sourcePublishedAt(),
                current.lastIngestedAt(),
                convertedNoteId,
                convertedIdeaId,
                current.createdAt(),
                Instant.parse("2026-04-11T10:10:00Z")
            );
            items.put(trendItemId, updated);
            return updated;
        }

        @Override
        public TrendItemRecord updateAnalysis(UUID trendItemId, UUID userId, TrendAnalysisPayload analysisPayload) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoopIdeaRepository implements IdeaRepository {

        @Override
        public IdeaRecord create(UUID userId, IdeaSourceMode sourceMode, UUID sourceNoteId, UUID sourceTrendItemId, String title, String rawDescription, IdeaStatus status, IdeaAssessmentResult assessmentResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<IdeaRecord> findAllByUserId(UUID userId) {
            return List.of();
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
            return Optional.empty();
        }
    }

    private static final class RecordingTrendIdeaConversionService extends TrendIdeaConversionService {

        private final List<PromoteToIdeaCommand> receivedCommands = new ArrayList<>();
        private final PromoteToIdeaResult resultToReturn;
        private final ApiException failure;

        private RecordingTrendIdeaConversionService(PromoteToIdeaResult resultToReturn, ApiException failure) {
            super(
                new InMemoryTrendItemRepository(),
                new NoopIdeaRepository(),
                new RecordingAgentTraceRepository(),
                new RecordingToolInvocationLogRepository(),
                new RecordingUserActionEventRepository(),
                transactionManager()
            );
            this.resultToReturn = resultToReturn;
            this.failure = failure;
        }

        @Override
        public PromoteToIdeaResult promoteToIdea(PromoteToIdeaCommand command) {
            receivedCommands.add(command);
            if (failure != null) {
                throw failure;
            }
            return resultToReturn;
        }
    }

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final List<UUID> createdTraceIds = new ArrayList<>();
        private final List<UUID> completedTraceIds = new ArrayList<>();
        private final List<UUID> failedTraceIds = new ArrayList<>();

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            UUID traceId = UUID.randomUUID();
            createdTraceIds.add(traceId);
            return traceId;
        }

        @Override
        public UUID create(UUID traceId, UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
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
        public void append(UUID userId, UUID traceId, String toolName, String status, Map<String, Object> inputDigest, Map<String, Object> outputDigest, Integer latencyMs, String errorCode, String errorMessage) {
            toolNames.add(toolName);
            statuses.add(status);
        }
    }

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {

        private final List<String> eventTypes = new ArrayList<>();
        private final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
            payloads.add(new LinkedHashMap<>(payload));
        }
    }
}
