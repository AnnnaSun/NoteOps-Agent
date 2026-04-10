package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.config.TrendProperties;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.junit.jupiter.api.Test;
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

class TrendPlanApplicationServiceTest {

    @Test
    void ingestsBothSourcesAndWritesTraceAndToolLogs() {
        UUID traceId = UUID.randomUUID();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository(traceId);
        RecordingToolInvocationLogRepository toolRepository = new RecordingToolInvocationLogRepository();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        TrendSourceRegistry registry = new TrendSourceRegistry(List.of(
            connector(
                TrendSourceType.HN,
                "Hacker News",
                List.of(candidate(TrendSourceType.HN, "hn-1", "Agent memory on HN", "https://news.ycombinator.com/item?id=1", 91.0))
            ),
            connector(
                TrendSourceType.GITHUB,
                "GitHub",
                List.of(candidate(TrendSourceType.GITHUB, "gh-1", "openai/codex", "https://github.com/openai/codex", 84.0))
            )
        ));
        TrendPlanApplicationService service = new TrendPlanApplicationService(
            trendProperties(true, List.of(TrendSourceType.HN, TrendSourceType.GITHUB)),
            registry,
            new TrendCandidateNormalizer(),
            trendItemRepository,
            traceRepository,
            toolRepository,
            transactionManager()
        );

        TrendPlanApplicationService.TriggerResult result = service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", traceId.toString())
        );

        assertThat(result.traceId()).isEqualTo(traceId.toString());
        assertThat(result.triggerMode()).isEqualTo("INGEST");
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.dedupedCount()).isEqualTo(0);
        assertThat(result.sourceResults()).hasSize(2);
        assertThat(traceRepository.createdTraceIds).containsExactly(traceId);
        assertThat(traceRepository.completedTraceIds).containsExactly(traceId);
        assertThat(toolRepository.toolNames).containsExactly(
            "trend.source_registry.resolve",
            "trend.source.fetch",
            "trend.source.fetch",
            "trend.item.upsert"
        );
        assertThat(toolRepository.statuses).containsOnly("COMPLETED");
        assertThat(trendItemRepository.itemsByCompositeKey).hasSize(2);
    }

    @Test
    void dedupesExistingTrendItemsOnRepeatedIngest() {
        UUID traceId = UUID.randomUUID();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        TrendPlanApplicationService service = new TrendPlanApplicationService(
            trendProperties(true, List.of(TrendSourceType.HN)),
            new TrendSourceRegistry(List.of(
                connector(
                    TrendSourceType.HN,
                    "Hacker News",
                    List.of(candidate(TrendSourceType.HN, "hn-1", "Agent memory on HN", "https://news.ycombinator.com/item?id=1", 91.0))
                )
            )),
            new TrendCandidateNormalizer(),
            trendItemRepository,
            new RecordingAgentTraceRepository(traceId),
            new RecordingToolInvocationLogRepository(),
            transactionManager()
        );

        TrendPlanApplicationService.TriggerResult first = service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", traceId.toString())
        );
        TrendPlanApplicationService.TriggerResult second = service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", UUID.randomUUID().toString())
        );

        assertThat(first.insertedCount()).isEqualTo(1);
        assertThat(first.dedupedCount()).isEqualTo(0);
        assertThat(second.insertedCount()).isEqualTo(0);
        assertThat(second.dedupedCount()).isEqualTo(1);
        assertThat(trendItemRepository.itemsByCompositeKey).hasSize(1);
    }

    @Test
    void rejectsDisabledDefaultPlanWithTrackedTrace() {
        UUID traceId = UUID.randomUUID();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository(traceId);
        RecordingToolInvocationLogRepository toolRepository = new RecordingToolInvocationLogRepository();
        TrendPlanApplicationService service = new TrendPlanApplicationService(
            trendProperties(false, List.of(TrendSourceType.HN, TrendSourceType.GITHUB)),
            new TrendSourceRegistry(List.of(
                connector(TrendSourceType.HN, "Hacker News", List.of()),
                connector(TrendSourceType.GITHUB, "GitHub", List.of())
            )),
            new TrendCandidateNormalizer(),
            new InMemoryTrendItemRepository(),
            traceRepository,
            toolRepository,
            transactionManager()
        );

        assertThatThrownBy(() -> service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", traceId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("default trend plan is disabled")
            .extracting("traceId")
            .isEqualTo(traceId.toString());
        assertThat(traceRepository.createdTraceIds).containsExactly(traceId);
        assertThat(traceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolRepository.toolNames).isEmpty();
    }

    @Test
    void failsWholeIngestWhenOneSourceFetchFails() {
        UUID traceId = UUID.randomUUID();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository(traceId);
        RecordingToolInvocationLogRepository toolRepository = new RecordingToolInvocationLogRepository();
        InMemoryTrendItemRepository trendItemRepository = new InMemoryTrendItemRepository();
        TrendPlanApplicationService service = new TrendPlanApplicationService(
            trendProperties(true, List.of(TrendSourceType.HN, TrendSourceType.GITHUB)),
            new TrendSourceRegistry(List.of(
                connector(TrendSourceType.HN, "Hacker News", List.of(candidate(TrendSourceType.HN, "hn-1", "Agent memory on HN", "https://news.ycombinator.com/item?id=1", 91.0))),
                failingConnector(TrendSourceType.GITHUB, "GitHub", new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "TREND_SOURCE_FETCH_FAILED", "GitHub fetch failed"))
            )),
            new TrendCandidateNormalizer(),
            trendItemRepository,
            traceRepository,
            toolRepository,
            transactionManager()
        );

        assertThatThrownBy(() -> service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", traceId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("GitHub fetch failed")
            .extracting("traceId")
            .isEqualTo(traceId.toString());
        assertThat(traceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolRepository.toolNames).contains("trend.source.fetch");
        assertThat(toolRepository.statuses).contains("FAILED");
        assertThat(trendItemRepository.itemsByCompositeKey).isEmpty();
    }

    @Test
    void marksUpsertFailuresWithTrendItemUpsertToolLog() {
        UUID traceId = UUID.randomUUID();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository(traceId);
        RecordingToolInvocationLogRepository toolRepository = new RecordingToolInvocationLogRepository();
        TrendPlanApplicationService service = new TrendPlanApplicationService(
            trendProperties(true, List.of(TrendSourceType.HN)),
            new TrendSourceRegistry(List.of(
                connector(
                    TrendSourceType.HN,
                    "Hacker News",
                    List.of(candidate(TrendSourceType.HN, "hn-1", "Agent memory on HN", "https://news.ycombinator.com/item?id=1", 91.0))
                )
            )),
            new TrendCandidateNormalizer(),
            new FailingUpsertTrendItemRepository(),
            traceRepository,
            toolRepository,
            transactionManager()
        );

        assertThatThrownBy(() -> service.triggerDefaultPlan(
            new TrendPlanApplicationService.TriggerCommand("11111111-1111-1111-1111-111111111111", traceId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("trend item upsert failed")
            .extracting("traceId")
            .isEqualTo(traceId.toString());
        assertThat(traceRepository.failedTraceIds).containsExactly(traceId);
        assertThat(toolRepository.toolNames).endsWith("trend.item.upsert");
        assertThat(toolRepository.statuses).endsWith("FAILED");
    }

    private TrendProperties trendProperties(boolean enabled, List<TrendSourceType> sources) {
        return new TrendProperties(
            new TrendProperties.DefaultPlan(
                "default_ai_engineering_trends",
                enabled,
                sources,
                5,
                "DAILY",
                List.of("agent", "llm", "memory", "retrieval", "tooling", "coding"),
                true,
                false
            )
        );
    }

    private FetchedTrendCandidate candidate(TrendSourceType sourceType,
                                            String sourceItemKey,
                                            String title,
                                            String url,
                                            double normalizedScore) {
        return new FetchedTrendCandidate(
            sourceType,
            sourceItemKey,
            title,
            url,
            normalizedScore,
            Instant.parse("2026-04-10T10:00:00Z"),
            Map.of("seed", sourceItemKey)
        );
    }

    private TrendSourceConnector connector(TrendSourceType sourceType, String displayName, List<FetchedTrendCandidate> candidates) {
        return new TrendSourceConnector() {
            @Override
            public TrendSourceType sourceType() {
                return sourceType;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<FetchedTrendCandidate> fetchCandidates(FetchCommand command) {
                return candidates;
            }
        };
    }

    private TrendSourceConnector failingConnector(TrendSourceType sourceType, String displayName, ApiException exception) {
        return new TrendSourceConnector() {
            @Override
            public TrendSourceType sourceType() {
                return sourceType;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<FetchedTrendCandidate> fetchCandidates(FetchCommand command) {
                throw exception;
            }
        };
    }

    private PlatformTransactionManager transactionManager() {
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

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final UUID traceId;
        private final List<UUID> createdTraceIds = new ArrayList<>();
        private final List<UUID> completedTraceIds = new ArrayList<>();
        private final List<UUID> failedTraceIds = new ArrayList<>();

        private RecordingAgentTraceRepository(UUID traceId) {
            this.traceId = traceId;
        }

        @Override
        public UUID create(UUID userId,
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

    private static class InMemoryTrendItemRepository implements TrendItemRepository {

        private final Map<String, TrendItemRecord> itemsByCompositeKey = new LinkedHashMap<>();

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
            TrendItemRecord created = new TrendItemRecord(
                UUID.randomUUID(),
                userId,
                sourceType,
                sourceItemKey,
                title,
                url,
                summary,
                normalizedScore,
                analysisPayload,
                extraAttributes,
                status,
                suggestedAction,
                sourcePublishedAt,
                lastIngestedAt,
                convertedNoteId,
                convertedIdeaId,
                Instant.now(),
                Instant.now()
            );
            itemsByCompositeKey.put(key(userId, sourceType, sourceItemKey), created);
            return created;
        }

        @Override
        public Optional<TrendItemRecord> findByIdAndUserId(UUID trendItemId, UUID userId) {
            return itemsByCompositeKey.values().stream()
                .filter(item -> item.id().equals(trendItemId) && item.userId().equals(userId))
                .findFirst();
        }

        @Override
        public Optional<TrendItemRecord> findBySourceKey(UUID userId, TrendSourceType sourceType, String sourceItemKey) {
            return Optional.ofNullable(itemsByCompositeKey.get(key(userId, sourceType, sourceItemKey)));
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
            String key = key(userId, sourceType, sourceItemKey);
            TrendItemRecord existing = itemsByCompositeKey.get(key);
            if (existing == null) {
                TrendItemRecord created = create(
                    userId,
                    sourceType,
                    sourceItemKey,
                    title,
                    url,
                    null,
                    normalizedScore,
                    TrendAnalysisPayload.empty(),
                    extraAttributes,
                    TrendItemStatus.INGESTED,
                    null,
                    sourcePublishedAt,
                    lastIngestedAt,
                    null,
                    null
                );
                return new TrendItemIngestResult(created, IngestAction.INSERTED);
            }

            TrendItemRecord deduped = new TrendItemRecord(
                existing.id(),
                existing.userId(),
                existing.sourceType(),
                existing.sourceItemKey(),
                existing.title(),
                existing.url(),
                existing.summary(),
                existing.normalizedScore(),
                existing.analysisPayload(),
                existing.extraAttributes(),
                existing.status(),
                existing.suggestedAction(),
                existing.sourcePublishedAt() == null ? sourcePublishedAt : existing.sourcePublishedAt(),
                lastIngestedAt,
                existing.convertedNoteId(),
                existing.convertedIdeaId(),
                existing.createdAt(),
                Instant.now()
            );
            itemsByCompositeKey.put(key, deduped);
            return new TrendItemIngestResult(deduped, IngestAction.DEDUPED);
        }

        @Override
        public List<TrendItemRecord> findAllByUserId(UUID userId) {
            return itemsByCompositeKey.values().stream().filter(item -> item.userId().equals(userId)).toList();
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

        private String key(UUID userId, TrendSourceType sourceType, String sourceItemKey) {
            return userId + ":" + sourceType.name() + ":" + sourceItemKey;
        }
    }

    private static final class FailingUpsertTrendItemRepository extends InMemoryTrendItemRepository {

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
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "TREND_ITEM_UPSERT_FAILED", "trend item upsert failed");
        }
    }
}
