package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TrendAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TrendAnalysisService.class);

    private final TrendItemRepository trendItemRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final TrendAgent trendAgent;
    private final TransactionTemplate transactionTemplate;

    public TrendAnalysisService(TrendItemRepository trendItemRepository,
                                ToolInvocationLogRepository toolInvocationLogRepository,
                                TrendAgent trendAgent,
                                PlatformTransactionManager transactionManager) {
        this.trendItemRepository = trendItemRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.trendAgent = trendAgent;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AnalysisSummary analyzePersistedItems(UUID userId,
                                                 UUID traceId,
                                                 List<TrendItemRepository.TrendItemRecord> trendItems) {
        List<TrendItemRepository.TrendItemRecord> uniqueItems = dedupeTrendItems(trendItems);
        int analyzedCount = 0;
        for (TrendItemRepository.TrendItemRecord trendItem : uniqueItems) {
            if (trendItem.status() != TrendItemStatus.INGESTED && trendItem.status() != TrendItemStatus.ANALYZED) {
                continue;
            }
            analyzeSingleItem(userId, traceId, trendItem);
            analyzedCount++;
        }
        return new AnalysisSummary(analyzedCount);
    }

    private TrendItemRepository.TrendItemRecord analyzeSingleItem(UUID userId,
                                                                  UUID traceId,
                                                                  TrendItemRepository.TrendItemRecord trendItem) {
        long startedAt = System.nanoTime();
        Map<String, Object> inputDigest = toolInputDigest(trendItem);
        log.info(
            "module=TrendAnalysisService action=trend_item_analyze_start trace_id={} user_id={} trend_item_id={} source_type={} result=RUNNING",
            traceId,
            userId,
            trendItem.id(),
            trendItem.sourceType().name()
        );
        try {
            TrendAnalysisPayload analysisPayload = analyzePayload(userId, traceId, trendItem);
            TrendItemRepository.TrendItemRecord updated = persistAnalysisResult(userId, traceId, trendItem, analysisPayload, inputDigest, startedAt);
            log.info(
                "module=TrendAnalysisService action=trend_item_analyze_success trace_id={} user_id={} trend_item_id={} suggested_action={} result=COMPLETED duration_ms={}",
                traceId,
                userId,
                updated.id(),
                updated.suggestedAction() == null ? null : updated.suggestedAction().name(),
                durationMs(startedAt)
            );
            return updated;
        } catch (ApiException exception) {
            appendFailedToolLog(userId, traceId, trendItem, inputDigest, startedAt, exception.errorCode(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            ApiException apiException = new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TREND_ANALYSIS_PERSIST_FAILED",
                exception.getMessage() == null ? "trend analysis persistence failed" : exception.getMessage()
            );
            appendFailedToolLog(userId, traceId, trendItem, inputDigest, startedAt, apiException.errorCode(), apiException.getMessage());
            throw apiException;
        }
    }

    private TrendAnalysisPayload analyzePayload(UUID userId,
                                                UUID traceId,
                                                TrendItemRepository.TrendItemRecord trendItem) {
        try {
            TrendAnalysisPayload analysisPayload = trendAgent.analyze(new AnalyzeTrendRequest(
                userId,
                traceId,
                trendItem.id(),
                trendItem.sourceType(),
                trendItem.sourceItemKey(),
                trendItem.title(),
                trendItem.url(),
                trendItem.normalizedScore(),
                trendItem.sourcePublishedAt(),
                trendItem.extraAttributes()
            ));
            validateAnalysisPayload(analysisPayload);
            return analysisPayload;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_ANALYSIS_FAILED",
                exception.getMessage() == null ? "trend analysis failed" : exception.getMessage()
            );
        }
    }

    private TrendItemRepository.TrendItemRecord persistAnalysisResult(UUID userId,
                                                                      UUID traceId,
                                                                      TrendItemRepository.TrendItemRecord trendItem,
                                                                      TrendAnalysisPayload analysisPayload,
                                                                      Map<String, Object> inputDigest,
                                                                      long startedAt) {
        TrendItemRepository.TrendItemRecord updated = transactionTemplate.execute(status -> {
            TrendItemRepository.TrendItemRecord persisted = trendItemRepository.updateAnalysis(
                trendItem.id(),
                userId,
                analysisPayload
            );
            int durationMs = durationMs(startedAt);
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "trend.item.analyze",
                "COMPLETED",
                inputDigest,
                toolOutputDigest(persisted),
                durationMs,
                null,
                null
            );
            return persisted;
        });
        if (updated == null) {
            throw new IllegalStateException("trend analysis transaction returned null");
        }
        return updated;
    }

    private void validateAnalysisPayload(TrendAnalysisPayload payload) {
        if (payload == null
            || isBlank(payload.summary())
            || isBlank(payload.whyItMatters())
            || isBlank(payload.signalType())
            || payload.topicTags() == null
            || payload.topicTags().isEmpty()
            || payload.suggestedAction() == null
            || isBlank(payload.reasoningSummary())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TREND_ANALYSIS_INVALID", "trend analysis payload is incomplete");
        }
    }

    private void appendFailedToolLog(UUID userId,
                                     UUID traceId,
                                     TrendItemRepository.TrendItemRecord trendItem,
                                     Map<String, Object> inputDigest,
                                     long startedAt,
                                     String errorCode,
                                     String errorMessage) {
        int durationMs = durationMs(startedAt);
        try {
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "trend.item.analyze",
                "FAILED",
                inputDigest,
                Map.of(),
                durationMs,
                errorCode,
                errorMessage
            );
        } catch (Exception appendException) {
            log.warn(
                "module=TrendAnalysisService action=trend_item_analyze_fail_log_append_fail trace_id={} user_id={} trend_item_id={} result=FAILED error_code={} error_message={}",
                traceId,
                userId,
                trendItem.id(),
                errorCode,
                appendException.getMessage()
            );
        }
        log.warn(
            "module=TrendAnalysisService action=trend_item_analyze_fail trace_id={} user_id={} trend_item_id={} result=FAILED duration_ms={} error_code={} error_message={}",
            traceId,
            userId,
            trendItem.id(),
            durationMs,
            errorCode,
            errorMessage
        );
    }

    private List<TrendItemRepository.TrendItemRecord> dedupeTrendItems(List<TrendItemRepository.TrendItemRecord> trendItems) {
        if (trendItems == null || trendItems.isEmpty()) {
            return List.of();
        }
        Set<UUID> seenIds = new LinkedHashSet<>();
        List<TrendItemRepository.TrendItemRecord> uniqueItems = new ArrayList<>();
        for (TrendItemRepository.TrendItemRecord trendItem : trendItems) {
            if (trendItem == null || !seenIds.add(trendItem.id())) {
                continue;
            }
            uniqueItems.add(trendItem);
        }
        return List.copyOf(uniqueItems);
    }

    private Map<String, Object> toolInputDigest(TrendItemRepository.TrendItemRecord trendItem) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", trendItem.id());
        digest.put("source_type", trendItem.sourceType().name());
        digest.put("source_item_key", trendItem.sourceItemKey());
        digest.put("title", trendItem.title());
        digest.put("normalized_score", trendItem.normalizedScore());
        return digest;
    }

    private Map<String, Object> toolOutputDigest(TrendItemRepository.TrendItemRecord updated) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", updated.id());
        digest.put("status", updated.status().name());
        digest.put("suggested_action", updated.suggestedAction() == null ? null : updated.suggestedAction().name());
        digest.put("topic_tags", updated.analysisPayload().topicTags());
        digest.put("note_worthy", updated.analysisPayload().noteWorthy());
        digest.put("idea_worthy", updated.analysisPayload().ideaWorthy());
        return digest;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public record AnalysisSummary(
        int analyzedCount
    ) {
    }
}
