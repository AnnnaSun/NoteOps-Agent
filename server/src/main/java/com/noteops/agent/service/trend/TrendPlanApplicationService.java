package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.config.TrendProperties;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrendPlanApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TrendPlanApplicationService.class);
    private static final String TRIGGER_MODE = "INGEST";

    private final TrendProperties trendProperties;
    private final TrendSourceRegistry trendSourceRegistry;
    private final TrendCandidateNormalizer trendCandidateNormalizer;
    private final TrendItemRepository trendItemRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final TransactionTemplate transactionTemplate;

    public TrendPlanApplicationService(TrendProperties trendProperties,
                                       TrendSourceRegistry trendSourceRegistry,
                                       TrendCandidateNormalizer trendCandidateNormalizer,
                                       TrendItemRepository trendItemRepository,
                                       AgentTraceRepository agentTraceRepository,
                                       ToolInvocationLogRepository toolInvocationLogRepository,
                                       PlatformTransactionManager transactionManager) {
        this.trendProperties = trendProperties;
        this.trendSourceRegistry = trendSourceRegistry;
        this.trendCandidateNormalizer = trendCandidateNormalizer;
        this.trendItemRepository = trendItemRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TriggerResult triggerDefaultPlan(TriggerCommand command) {
        UUID traceId = parseUuid(command.traceId(), "INVALID_TRACE_ID", "trace_id must be a valid UUID", null);
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID", traceId.toString());
        TrendProperties.DefaultPlan defaultPlan = trendProperties.defaultPlan();

        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("plan_key", defaultPlan == null ? null : defaultPlan.planKey());
        traceState.put(
            "source_types",
            defaultPlan == null || defaultPlan.sources() == null
                ? List.of()
                : defaultPlan.sources().stream().map(Enum::name).toList()
        );
        traceState.put("trigger_mode", TRIGGER_MODE);
        agentTraceRepository.create(
            traceId,
            userId,
            "TREND_INGEST",
            "Ingest default trend plan " + (defaultPlan == null ? "unknown" : defaultPlan.planKey()),
            "TREND_PLAN",
            null,
            List.of("trend-source-fetch", "trend-candidate-normalize", "trend-item-upsert"),
            traceState
        );

        log.info(
            "module=TrendPlanApplicationService action=trend_ingest_start trace_id={} user_id={} plan_key={} source_types={} result=RUNNING",
            traceId,
            userId,
            traceState.get("plan_key"),
            traceState.get("source_types")
        );

        long startedAt = System.nanoTime();
        String activeToolName = null;
        Map<String, Object> activeInputDigest = Map.of();

        try {
            validateDefaultPlan(defaultPlan);
            activeToolName = "trend.source_registry.resolve";
            activeInputDigest = Map.of(
                "plan_key", traceState.get("plan_key"),
                "source_types", traceState.get("source_types")
            );
            List<TrendSourceRegistry.ResolvedTrendSource> resolvedSources = trendSourceRegistry.resolveAll(defaultPlan.sources());
            appendToolLog(userId, traceId, activeToolName, "COMPLETED", activeInputDigest, Map.of(
                "resolved_sources", resolvedSources.stream()
                    .map(source -> Map.of(
                        "source_type", source.sourceType().name(),
                        "display_name", source.displayName()
                    ))
                    .toList(),
                "trigger_mode", TRIGGER_MODE
            ), null, null, durationMs(startedAt));

            List<FetchedSourceBatch> fetchedSourceBatches = new ArrayList<>();

            for (TrendSourceRegistry.ResolvedTrendSource source : resolvedSources) {
                TrendSourceConnector connector = trendSourceRegistry.getRequired(source.sourceType());
                long sourceStartedAt = System.nanoTime();
                activeToolName = "trend.source.fetch";
                activeInputDigest = Map.of(
                    "source_type", source.sourceType().name(),
                    "fetch_limit", defaultPlan.fetchLimitPerSource(),
                    "keyword_bias", defaultPlan.keywordBias()
                );
                log.info(
                    "module=TrendPlanApplicationService action=trend_source_fetch_start trace_id={} user_id={} source_type={} result=RUNNING",
                    traceId,
                    userId,
                    source.sourceType().name()
                );
                List<FetchedTrendCandidate> fetchedCandidates = connector.fetchCandidates(
                    new TrendSourceConnector.FetchCommand(
                        userId,
                        traceId,
                        defaultPlan.fetchLimitPerSource(),
                        defaultPlan.keywordBias()
                    )
                );
                int sourceDurationMs = durationMs(sourceStartedAt);
                appendToolLog(userId, traceId, activeToolName, "COMPLETED", activeInputDigest, Map.of(
                    "source_type", source.sourceType().name(),
                    "fetched_count", fetchedCandidates.size()
                ), null, null, sourceDurationMs);
                log.info(
                    "module=TrendPlanApplicationService action=trend_source_fetch_success trace_id={} user_id={} source_type={} result=COMPLETED duration_ms={} fetched_count={}",
                    traceId,
                    userId,
                    source.sourceType().name(),
                    sourceDurationMs,
                    fetchedCandidates.size()
                );
                fetchedSourceBatches.add(new FetchedSourceBatch(source, fetchedCandidates));
            }

            activeToolName = "trend.item.upsert";
            activeInputDigest = Map.of(
                "plan_key", defaultPlan.planKey(),
                "source_types", defaultPlan.sources().stream().map(TrendSourceType::name).toList()
            );
            PersistSummary persistSummary = transactionTemplate.execute(status ->
                persistFetchedSourceBatches(userId, fetchedSourceBatches, Instant.now())
            );
            if (persistSummary == null) {
                throw new IllegalStateException("trend item upsert transaction returned null");
            }
            appendToolLog(userId, traceId, activeToolName, "COMPLETED", activeInputDigest, Map.of(
                "fetched_count", persistSummary.fetchedCount(),
                "inserted_count", persistSummary.insertedCount(),
                "deduped_count", persistSummary.dedupedCount()
            ), null, null, durationMs(startedAt));
            log.info(
                "module=TrendPlanApplicationService action=trend_ingest_persist_summary trace_id={} user_id={} result=COMPLETED fetched_count={} inserted_count={} deduped_count={}",
                traceId,
                userId,
                persistSummary.fetchedCount(),
                persistSummary.insertedCount(),
                persistSummary.dedupedCount()
            );

            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("fetched_count", persistSummary.fetchedCount());
            completedState.put("inserted_count", persistSummary.insertedCount());
            completedState.put("deduped_count", persistSummary.dedupedCount());
            completedState.put("result", "COMPLETED");
            agentTraceRepository.markCompleted(traceId, "Ingested default trend plan candidates", completedState);

            int totalDurationMs = durationMs(startedAt);
            log.info(
                "module=TrendPlanApplicationService action=trend_ingest_success trace_id={} user_id={} plan_key={} result=COMPLETED duration_ms={} fetched_count={} inserted_count={} deduped_count={}",
                traceId,
                userId,
                defaultPlan.planKey(),
                totalDurationMs,
                persistSummary.fetchedCount(),
                persistSummary.insertedCount(),
                persistSummary.dedupedCount()
            );

            return new TriggerResult(
                defaultPlan.planKey(),
                defaultPlan.enabled(),
                resolvedSources,
                defaultPlan.fetchLimitPerSource(),
                defaultPlan.schedule(),
                defaultPlan.keywordBias(),
                defaultPlan.autoIngest(),
                defaultPlan.autoConvert(),
                TRIGGER_MODE,
                persistSummary.fetchedCount(),
                persistSummary.insertedCount(),
                persistSummary.dedupedCount(),
                persistSummary.sourceResults(),
                "COMPLETED",
                traceId.toString()
            );
        } catch (ApiException exception) {
            handleFailure(traceId, userId, defaultPlan, traceState, activeToolName, activeInputDigest, exception, startedAt);
            throw new ApiException(exception.httpStatus(), exception.errorCode(), exception.getMessage(), traceId.toString());
        } catch (Exception exception) {
            ApiException apiException = new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TREND_INGEST_FAILED",
                exception.getMessage() == null ? "trend ingest failed" : exception.getMessage(),
                traceId.toString()
            );
            handleFailure(traceId, userId, defaultPlan, traceState, activeToolName, activeInputDigest, apiException, startedAt);
            throw apiException;
        }
    }

    private void handleFailure(UUID traceId,
                               UUID userId,
                               TrendProperties.DefaultPlan defaultPlan,
                               Map<String, Object> traceState,
                               String activeToolName,
                               Map<String, Object> activeInputDigest,
                               ApiException exception,
                               long startedAt) {
        int durationMs = durationMs(startedAt);
        if ("trend.source_registry.resolve".equals(activeToolName)
            || "trend.source.fetch".equals(activeToolName)
            || "trend.item.upsert".equals(activeToolName)) {
            appendToolLog(userId, traceId, activeToolName, "FAILED", activeInputDigest, Map.of(), exception.errorCode(), exception.getMessage(), durationMs);
        }
        Map<String, Object> failedState = new LinkedHashMap<>(traceState);
        failedState.put("result", "FAILED");
        failedState.put("error_code", exception.errorCode());
        failedState.put("error_message", exception.getMessage());
        agentTraceRepository.markFailed(traceId, "Failed to ingest default trend plan candidates", failedState);
        log.warn(
            "module=TrendPlanApplicationService action=trend_ingest_fail trace_id={} user_id={} plan_key={} source_types={} result=FAILED duration_ms={} error_code={} error_message={}",
            traceId,
            userId,
            traceState.get("plan_key"),
            traceState.get("source_types"),
            durationMs,
            exception.errorCode(),
            exception.getMessage()
        );
    }

    private void appendToolLog(UUID userId,
                               UUID traceId,
                               String toolName,
                               String status,
                               Map<String, Object> inputDigest,
                               Map<String, Object> outputDigest,
                               String errorCode,
                               String errorMessage,
                               Integer latencyMs) {
        toolInvocationLogRepository.append(
            userId,
            traceId,
            toolName,
            status,
            inputDigest,
            outputDigest,
            latencyMs,
            errorCode,
            errorMessage
        );
    }

    private PersistSummary persistFetchedSourceBatches(UUID userId,
                                                       List<FetchedSourceBatch> fetchedSourceBatches,
                                                       Instant lastIngestedAt) {
        List<SourceResult> sourceResults = new ArrayList<>();
        int fetchedCount = 0;
        int insertedCount = 0;
        int dedupedCount = 0;

        for (FetchedSourceBatch batch : fetchedSourceBatches) {
            int sourceInsertedCount = 0;
            int sourceDedupedCount = 0;
            for (FetchedTrendCandidate fetchedCandidate : batch.candidates()) {
                FetchedTrendCandidate normalizedCandidate = trendCandidateNormalizer.normalize(fetchedCandidate);
                TrendItemRepository.TrendItemIngestResult ingestResult = trendItemRepository.upsertIngested(
                    userId,
                    normalizedCandidate.sourceType(),
                    normalizedCandidate.sourceItemKey(),
                    normalizedCandidate.title(),
                    normalizedCandidate.url(),
                    normalizedCandidate.normalizedScore(),
                    normalizedCandidate.extraAttributes(),
                    normalizedCandidate.sourcePublishedAt(),
                    lastIngestedAt
                );
                if (ingestResult.action() == TrendItemRepository.IngestAction.INSERTED) {
                    sourceInsertedCount++;
                } else {
                    sourceDedupedCount++;
                }
            }

            fetchedCount += batch.candidates().size();
            insertedCount += sourceInsertedCount;
            dedupedCount += sourceDedupedCount;
            sourceResults.add(new SourceResult(
                batch.source().sourceType().name(),
                batch.source().displayName(),
                batch.candidates().size(),
                sourceInsertedCount,
                sourceDedupedCount
            ));
        }

        return new PersistSummary(fetchedCount, insertedCount, dedupedCount, sourceResults);
    }

    private void validateDefaultPlan(TrendProperties.DefaultPlan defaultPlan) {
        if (defaultPlan == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TREND_DEFAULT_PLAN_MISSING", "default trend plan is missing");
        }
        if (!defaultPlan.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "TREND_DEFAULT_PLAN_DISABLED", "default trend plan is disabled");
        }
        if (defaultPlan.sources() == null || defaultPlan.sources().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TREND_DEFAULT_PLAN_INVALID", "default trend plan must configure at least one source");
        }
    }

    private UUID parseUuid(String raw, String errorCode, String message, String traceId) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message, traceId);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message, traceId);
        }
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public record TriggerCommand(
        String userId,
        String traceId
    ) {
    }

    public record TriggerResult(
        String planKey,
        boolean enabled,
        List<TrendSourceRegistry.ResolvedTrendSource> resolvedSources,
        int fetchLimitPerSource,
        String schedule,
        List<String> keywordBias,
        boolean autoIngest,
        boolean autoConvert,
        String triggerMode,
        int fetchedCount,
        int insertedCount,
        int dedupedCount,
        List<SourceResult> sourceResults,
        String result,
        String traceId
    ) {
    }

    public record SourceResult(
        String sourceType,
        String displayName,
        int fetchedCount,
        int insertedCount,
        int dedupedCount
    ) {
    }

    private record FetchedSourceBatch(
        TrendSourceRegistry.ResolvedTrendSource source,
        List<FetchedTrendCandidate> candidates
    ) {
    }

    private record PersistSummary(
        int fetchedCount,
        int insertedCount,
        int dedupedCount,
        List<SourceResult> sourceResults
    ) {
    }
}
