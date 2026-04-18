package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.dto.trend.TrendInboxItemResponse;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trend.TrendItemRepository;
import com.noteops.agent.service.preference.PreferenceContextInjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TrendInboxQueryService {

    private static final Logger log = LoggerFactory.getLogger(TrendInboxQueryService.class);
    private static final String PREFERENCE_CONTEXT_LOAD_FAILED = "PREFERENCE_CONTEXT_LOAD_FAILED";

    private final TrendItemRepository trendItemRepository;
    private final PreferenceContextInjectionService preferenceContextInjectionService;

    @Autowired
    public TrendInboxQueryService(TrendItemRepository trendItemRepository,
                                  PreferenceContextInjectionService preferenceContextInjectionService) {
        this.trendItemRepository = trendItemRepository;
        this.preferenceContextInjectionService = preferenceContextInjectionService;
    }

    public TrendInboxQueryService(TrendItemRepository trendItemRepository) {
        this(trendItemRepository, PreferenceContextInjectionService.noOp());
    }

    public List<TrendInboxItemResponse> list(InboxQueryCommand command) {
        long startedAt = System.nanoTime();
        log.info(
            "module=TrendInboxQueryService action=trend_inbox_list_start trace_id={} user_id={} status={} source_type={} result=RUNNING",
            command.traceId(),
            command.userId(),
            command.status(),
            command.sourceType()
        );
        try {
            UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID", command.traceId());
            TrendItemStatus status = parseStatus(command.status(), command.traceId());
            TrendSourceType sourceType = parseSourceType(command.sourceType(), command.traceId());
            List<TrendItemRepository.TrendItemRecord> originalRecords = trendItemRepository.findInboxByUserId(userId, status, sourceType);
            PreferenceContextInjectionService.PreferenceContext preferenceContext = loadPreferenceContextSafely(userId, command.traceId());
            List<RankedTrendItem> rankedItems = rankWithPreference(originalRecords, preferenceContext);
            int preferenceRerankCount = countOrderShift(originalRecords, rankedItems);
            int suggestedActionOverrideCount = (int) rankedItems.stream()
                .filter(RankedTrendItem::suggestedActionOverridden)
                .count();
            List<TrendInboxItemResponse> items = rankedItems.stream()
                .map(item -> TrendInboxItemResponse.from(item.record(), item.effectiveSuggestedAction()))
                .toList();
            log.info(
                "module=TrendInboxQueryService action=trend_inbox_list_success trace_id={} user_id={} status={} source_type={} result=COMPLETED duration_ms={} item_count={} preference_profile_loaded={} preference_rerank_count={} suggested_action_override_count={}",
                command.traceId(),
                userId,
                status.name(),
                sourceType == null ? null : sourceType.name(),
                durationMs(startedAt),
                items.size(),
                preferenceContext.profileLoaded(),
                preferenceRerankCount,
                suggestedActionOverrideCount
            );
            return items;
        } catch (ApiException exception) {
            log.warn(
                "module=TrendInboxQueryService action=trend_inbox_list_fail trace_id={} user_id={} status={} source_type={} result=FAILED duration_ms={} error_code={} error_message={}",
                command.traceId(),
                command.userId(),
                command.status(),
                command.sourceType(),
                durationMs(startedAt),
                exception.errorCode(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            String message = "trend inbox query failed";
            ApiException apiException = new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TREND_INBOX_QUERY_FAILED",
                message,
                command.traceId()
            );
            log.warn(
                "module=TrendInboxQueryService action=trend_inbox_list_fail trace_id={} user_id={} status={} source_type={} result=FAILED duration_ms={} error_code={} error_message={}",
                command.traceId(),
                command.userId(),
                command.status(),
                command.sourceType(),
                durationMs(startedAt),
                apiException.errorCode(),
                exception.getMessage()
            );
            throw apiException;
        }
    }

    private UUID parseUuid(String rawValue, String errorCode, String message, String traceId) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message, traceId);
        }
    }

    private TrendItemStatus parseStatus(String rawValue, String traceId) {
        if (rawValue == null || rawValue.isBlank()) {
            return TrendItemStatus.ANALYZED;
        }
        try {
            return TrendItemStatus.valueOf(rawValue.trim());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TREND_STATUS", "status must be a valid trend item status", traceId);
        }
    }

    private TrendSourceType parseSourceType(String rawValue, String traceId) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return TrendSourceType.valueOf(rawValue.trim());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TREND_SOURCE_TYPE", "source_type must be a valid trend source type", traceId);
        }
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private PreferenceContextInjectionService.PreferenceContext loadPreferenceContextSafely(UUID userId, String traceId) {
        try {
            return preferenceContextInjectionService.loadContext(userId);
        } catch (RuntimeException exception) {
            log.warn(
                "module=TrendInboxQueryService action=trend_inbox_preference_context_degraded trace_id={} user_id={} result=DEGRADED error_code={} error_message={}",
                traceId,
                userId,
                PREFERENCE_CONTEXT_LOAD_FAILED,
                exception.getMessage()
            );
            return PreferenceContextInjectionService.emptyContext();
        }
    }

    private List<RankedTrendItem> rankWithPreference(List<TrendItemRepository.TrendItemRecord> records,
                                                     PreferenceContextInjectionService.PreferenceContext context) {
        if (records.isEmpty()) {
            return List.of();
        }

        List<RankedTrendItem> ranked = new ArrayList<>(records.size());
        for (TrendItemRepository.TrendItemRecord record : records) {
            String originalSuggestedAction = record.suggestedAction() == null ? null : record.suggestedAction().name();
            if (!context.profileLoaded()) {
                ranked.add(new RankedTrendItem(record, originalSuggestedAction, record.normalizedScore(), false));
                continue;
            }
            PreferenceContextInjectionService.TrendInjectionResult injectionResult = preferenceContextInjectionService.injectTrend(
                context,
                new PreferenceContextInjectionService.TrendCandidate(
                    record.sourceType().name(),
                    record.analysisPayload().topicTags(),
                    record.analysisPayload().noteWorthy(),
                    record.analysisPayload().ideaWorthy(),
                    originalSuggestedAction
                )
            );
            ranked.add(new RankedTrendItem(
                record,
                injectionResult.effectiveSuggestedAction(),
                record.normalizedScore() + injectionResult.rankingDelta(),
                injectionResult.overridden()
            ));
        }

        if (!context.profileLoaded()) {
            return List.copyOf(ranked);
        }

        ranked.sort(Comparator
            .comparingDouble(RankedTrendItem::rankingScore).reversed()
            .thenComparing(item -> item.record().updatedAt(), Comparator.reverseOrder())
            .thenComparing(item -> item.record().id()));
        return List.copyOf(ranked);
    }

    private int countOrderShift(List<TrendItemRepository.TrendItemRecord> originalRecords, List<RankedTrendItem> rankedItems) {
        int positionCount = Math.min(originalRecords.size(), rankedItems.size());
        int movedCount = 0;
        for (int index = 0; index < positionCount; index++) {
            if (!originalRecords.get(index).id().equals(rankedItems.get(index).record().id())) {
                movedCount++;
            }
        }
        return movedCount;
    }

    public record InboxQueryCommand(
        String userId,
        String status,
        String sourceType,
        String traceId
    ) {
    }

    private record RankedTrendItem(
        TrendItemRepository.TrendItemRecord record,
        String effectiveSuggestedAction,
        double rankingScore,
        boolean suggestedActionOverridden
    ) {
    }
}
