package com.noteops.agent.service.search;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.search.SearchRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SearchApplicationService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final SearchAiEnhancer NO_OP_SEARCH_AI_ENHANCER = request ->
        new SearchAiEnhancer.SearchAiEnhancementResult(Map.of(), Map.of());

    private final SearchRepository searchRepository;
    private final SearchAiEnhancer searchAiEnhancer;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    @Autowired
    public SearchApplicationService(SearchRepository searchRepository,
                                    SearchAiEnhancer searchAiEnhancer,
                                    AgentTraceRepository agentTraceRepository,
                                    ToolInvocationLogRepository toolInvocationLogRepository,
                                    UserActionEventRepository userActionEventRepository) {
        this.searchRepository = searchRepository;
        this.searchAiEnhancer = searchAiEnhancer;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    public SearchApplicationService(SearchRepository searchRepository) {
        this(
            searchRepository,
            NO_OP_SEARCH_AI_ENHANCER,
            new NoOpAgentTraceRepository(),
            NO_OP_TOOL_INVOCATION_LOG_REPOSITORY,
            NO_OP_USER_ACTION_EVENT_REPOSITORY
        );
    }

    @Transactional
    // 搜索入口：保留 exact / related / external 三分栏，并补 AI 关系解释与结构化增强。
    public SearchView search(String userIdRaw, String queryRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        String query = requireQuery(userId, queryRaw);
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isBlank()) {
            log.warn("module=SearchApplicationService action=search_query_rejected user_id={} error_code=INVALID_SEARCH_QUERY error_message=query must not be blank trace_id=null",
                userId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must not be blank");
        }
        Set<String> queryTokens = tokenize(normalizedQuery);

        long startedAt = System.nanoTime();
        UUID searchRequestId = UUID.randomUUID();
        UUID traceId = agentTraceRepository.create(
            userId,
            "SEARCH_QUERY",
            "Search query for " + query,
            "SEARCH_QUERY",
            searchRequestId,
            List.of("search-worker"),
            Map.of(
                "query", query,
                "normalized_query", normalizedQuery,
                "search_request_id", searchRequestId
            )
        );
        log.info(
            "module=SearchApplicationService action=search_query_start path=/api/v1/search user_id={} search_request_id={} trace_id={} query_length={}",
            userId,
            searchRequestId,
            traceId,
            query.length()
        );

        List<SearchRepository.SearchCandidate> candidates = searchRepository.findByUserId(userId);
        List<ScoredExactMatch> exactMatches = new ArrayList<>();
        Set<UUID> exactNoteIds = new HashSet<>();
        for (SearchRepository.SearchCandidate candidate : candidates) {
            exactMatch(candidate, normalizedQuery).ifPresent(match -> {
                exactMatches.add(match);
                exactNoteIds.add(candidate.noteId());
            });
        }

        List<ScoredRelatedMatch> relatedMatches = new ArrayList<>();
        for (SearchRepository.SearchCandidate candidate : candidates) {
            if (exactNoteIds.contains(candidate.noteId())) {
                continue;
            }
            relatedMatch(candidate, queryTokens).ifPresent(relatedMatches::add);
        }

        exactMatches.sort(Comparator
            .comparingInt(ScoredExactMatch::score).reversed()
            .thenComparing(match -> match.view().updatedAt(), Comparator.reverseOrder())
            .thenComparing(match -> match.view().noteId()));
        relatedMatches.sort(Comparator
            .comparingInt(ScoredRelatedMatch::score).reversed()
            .thenComparing(match -> match.view().updatedAt(), Comparator.reverseOrder())
            .thenComparing(match -> match.view().noteId()));

        List<ExternalSupplementSeed> externalSupplementSeeds = buildExternalSupplementSeeds(query);
        EnhancementOutcome enhancementOutcome = enhanceSearchArtifacts(
            userId,
            traceId,
            searchRequestId,
            query,
            relatedMatches,
            externalSupplementSeeds
        );
        List<SearchRelatedMatchView> relatedViews = relatedMatches.stream()
            .map(match -> match.view().withRelationReason(resolveRelationReason(match.view(), enhancementOutcome.result())))
            .toList();
        List<ExternalSupplementView> externalSupplements = externalSupplementSeeds.stream()
            .map(seed -> toExternalSupplementView(seed, enhancementOutcome.result()))
            .toList();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "search.execute",
            "COMPLETED",
            Map.of(
                "query", query,
                "search_request_id", searchRequestId
            ),
            Map.of(
                "ai_enhancement_status", enhancementOutcome.status(),
                "exact_count", exactMatches.size(),
                "related_count", relatedViews.size(),
                "external_count", externalSupplements.size()
            ),
            toLatencyMs(durationMs),
            null,
            null
        );
        toolInvocationLogRepository.append(
            userId,
            traceId,
            "search.external-supplement-generator",
            "COMPLETED",
            Map.of(
                "query", query,
                "search_request_id", searchRequestId
            ),
            Map.of(
                "ai_enhancement_status", enhancementOutcome.status(),
                "external_count", externalSupplements.size(),
                "source_names", externalSupplements.stream().map(ExternalSupplementView::sourceName).toList(),
                "relation_labels", externalSupplements.stream().map(ExternalSupplementView::relationLabel).toList()
            ),
            toLatencyMs(enhancementOutcome.durationMs()),
            null,
            null
        );
        userActionEventRepository.append(
            userId,
            "SEARCH_EXECUTED",
            "SEARCH_QUERY",
            searchRequestId,
            traceId,
            Map.of(
                "query", query,
                "exact_count", exactMatches.size(),
                "related_count", relatedViews.size(),
                "external_count", externalSupplements.size(),
                "ai_enhancement_status", enhancementOutcome.status()
            )
        );
        userActionEventRepository.append(
            userId,
            "SEARCH_EXTERNAL_SUPPLEMENTS_GENERATED",
            "SEARCH_QUERY",
            searchRequestId,
            traceId,
            Map.of(
                "query", query,
                "exact_count", exactMatches.size(),
                "related_count", relatedViews.size(),
                "external_count", externalSupplements.size(),
                "ai_enhancement_status", enhancementOutcome.status(),
                "relation_labels", externalSupplements.stream().map(ExternalSupplementView::relationLabel).toList()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Search completed with " + exactMatches.size() + " exact matches, " + relatedViews.size() + " related matches and " + externalSupplements.size() + " external supplements",
            Map.of(
                "search_request_id", searchRequestId,
                "query", query,
                "exact_count", exactMatches.size(),
                "related_count", relatedViews.size(),
                "external_count", externalSupplements.size(),
                "ai_enhancement_status", enhancementOutcome.status()
            )
        );
        log.info(
            "module=SearchApplicationService action=search_query_success path=/api/v1/search user_id={} search_request_id={} trace_id={} exact_count={} related_count={} external_count={} ai_enhancement_status={} duration_ms={}",
            userId,
            searchRequestId,
            traceId,
            exactMatches.size(),
            relatedViews.size(),
            externalSupplements.size(),
            enhancementOutcome.status(),
            durationMs
        );

        return new SearchView(
            query,
            exactMatches.stream().map(ScoredExactMatch::view).toList(),
            relatedViews,
            externalSupplements
        );
    }

    private Optional<ScoredExactMatch> exactMatch(SearchRepository.SearchCandidate candidate, String normalizedQuery) {
        MatchField field = exactMatchField(candidate, normalizedQuery);
        if (field == null) {
            return Optional.empty();
        }
        int score = switch (field) {
            case TITLE -> 400;
            case SUMMARY -> 300;
            case KEY_POINTS, TAGS -> 200;
            case LATEST_CONTENT -> 100;
        };
        return Optional.of(new ScoredExactMatch(toExactView(candidate), score));
    }

    private MatchField exactMatchField(SearchRepository.SearchCandidate candidate, String normalizedQuery) {
        if (containsNormalized(candidate.title(), normalizedQuery)) {
            return MatchField.TITLE;
        }
        if (containsNormalized(candidate.currentSummary(), normalizedQuery)) {
            return MatchField.SUMMARY;
        }
        if (containsNormalized(String.join(" ", candidate.currentKeyPoints()), normalizedQuery)) {
            return MatchField.KEY_POINTS;
        }
        if (containsNormalized(String.join(" ", candidate.currentTags()), normalizedQuery)) {
            return MatchField.TAGS;
        }
        if (containsNormalized(candidate.latestContent(), normalizedQuery)) {
            return MatchField.LATEST_CONTENT;
        }
        return null;
    }

    private Optional<ScoredRelatedMatch> relatedMatch(SearchRepository.SearchCandidate candidate, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return Optional.empty();
        }

        List<FieldOverlap> overlaps = List.of(
            overlap(candidate.title(), queryTokens, MatchField.TITLE, 5),
            overlap(candidate.currentSummary(), queryTokens, MatchField.SUMMARY, 4),
            overlap(String.join(" ", candidate.currentKeyPoints()), queryTokens, MatchField.KEY_POINTS, 3),
            overlap(String.join(" ", candidate.currentTags()), queryTokens, MatchField.TAGS, 2),
            overlap(candidate.latestContent(), queryTokens, MatchField.LATEST_CONTENT, 1)
        ).stream()
            .filter(overlap -> overlap != null)
            .filter(overlap -> overlap.sharedTokenCount() > 0)
            .toList();

        if (overlaps.isEmpty()) {
            return Optional.empty();
        }

        FieldOverlap best = overlaps.stream()
            .max(Comparator
                .comparingInt(FieldOverlap::sharedTokenCount)
                .thenComparingInt(FieldOverlap::priority))
            .orElseThrow();

        int score = best.sharedTokenCount() * 10 + best.priority();
        return Optional.of(new ScoredRelatedMatch(
            toRelatedView(candidate, descriptiveRelationReason(best)),
            candidate,
            score
        ));
    }

    private FieldOverlap overlap(String fieldValue, Set<String> queryTokens, MatchField field, int priority) {
        Set<String> fieldTokens = tokenize(fieldValue);
        fieldTokens.retainAll(queryTokens);
        return new FieldOverlap(field, fieldTokens.stream().limit(3).toList(), fieldTokens.size(), priority);
    }

    private SearchExactMatchView toExactView(SearchRepository.SearchCandidate candidate) {
        return new SearchExactMatchView(
            candidate.noteId(),
            candidate.title(),
            candidate.currentSummary(),
            candidate.currentKeyPoints(),
            candidate.latestContent(),
            candidate.updatedAt()
        );
    }

    private SearchRelatedMatchView toRelatedView(SearchRepository.SearchCandidate candidate, String relationReason) {
        return new SearchRelatedMatchView(
            candidate.noteId(),
            candidate.title(),
            candidate.currentSummary(),
            candidate.currentKeyPoints(),
            candidate.latestContent(),
            relationReason,
            candidate.updatedAt()
        );
    }

    private List<ExternalSupplementSeed> buildExternalSupplementSeeds(String query) {
        List<String> keywords = buildKeywords(query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return List.of(
            new ExternalSupplementSeed(
                "Search Stub Background",
                "stub://search/background?q=" + encodedQuery,
                "Background reading related to " + query,
                keywords,
                List.of("BACKGROUND"),
                "背景补充",
                "背景资料聚焦 " + query
            ),
            new ExternalSupplementSeed(
                "Search Stub Follow-up",
                "stub://search/follow-up?q=" + encodedQuery,
                "Follow-up reading related to " + query,
                keywords,
                List.of("FOLLOW_UP"),
                "延伸阅读",
                "延伸线索聚焦 " + query
            )
        );
    }

    private EnhancementOutcome enhanceSearchArtifacts(UUID userId,
                                                      UUID traceId,
                                                      UUID searchRequestId,
                                                      String query,
                                                      List<ScoredRelatedMatch> relatedMatches,
                                                      List<ExternalSupplementSeed> externalSupplementSeeds) {
        long startedAt = System.nanoTime();
        if (relatedMatches.isEmpty() && externalSupplementSeeds.isEmpty()) {
            return new EnhancementOutcome("SKIPPED", 0, new SearchAiEnhancer.SearchAiEnhancementResult(Map.of(), Map.of()));
        }

        SearchAiEnhancer.SearchAiEnhancementRequest request = new SearchAiEnhancer.SearchAiEnhancementRequest(
            userId,
            traceId,
            query,
            relatedMatches.stream()
                .map(match -> new SearchAiEnhancer.RelatedCandidate(
                    match.view().noteId(),
                    match.view().title(),
                    match.view().currentSummary(),
                    match.view().currentKeyPoints(),
                    match.candidate().currentTags(),
                    match.candidate().sourceUri(),
                    match.candidate().latestContentType(),
                    match.view().relationReason()
                ))
                .toList(),
            externalSupplementSeeds.stream()
                .map(seed -> new SearchAiEnhancer.ExternalCandidate(
                    seed.sourceName(),
                    seed.sourceUri(),
                    seed.summary(),
                    seed.keywords(),
                    seed.relationTags(),
                    seed.fallbackRelationLabel(),
                    seed.fallbackSummarySnippet()
                ))
                .toList()
        );

        try {
            SearchAiEnhancer.SearchAiEnhancementResult result = searchAiEnhancer.enhance(request);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "search.ai-enhancement",
                "COMPLETED",
                Map.of(
                    "search_request_id", searchRequestId,
                    "query", query,
                    "related_candidate_count", relatedMatches.size(),
                    "external_candidate_count", externalSupplementSeeds.size()
                ),
                Map.of(
                    "related_enhanced_count", result.relatedByNoteId().size(),
                    "external_enhanced_count", result.externalBySourceUri().size()
                ),
                toLatencyMs(durationMs),
                null,
                null
            );
            return new EnhancementOutcome("COMPLETED", durationMs, result);
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.warn(
                "module=SearchApplicationService action=search_ai_enhancement_failed path=/api/v1/search user_id={} trace_id={} error_code=SEARCH_AI_ENHANCEMENT_FAILED error_message={}",
                userId,
                traceId,
                exception.getMessage()
            );
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "search.ai-enhancement",
                "FAILED",
                Map.of(
                    "search_request_id", searchRequestId,
                    "query", query,
                    "related_candidate_count", relatedMatches.size(),
                    "external_candidate_count", externalSupplementSeeds.size()
                ),
                Map.of(),
                toLatencyMs(durationMs),
                "SEARCH_AI_ENHANCEMENT_FAILED",
                exception.getMessage()
            );
            userActionEventRepository.append(
                userId,
                "SEARCH_AI_ENHANCEMENT_DEGRADED",
                "SEARCH_QUERY",
                searchRequestId,
                traceId,
                Map.of(
                    "query", query,
                    "related_candidate_count", relatedMatches.size(),
                    "external_candidate_count", externalSupplementSeeds.size(),
                    "error_message", exception.getMessage()
                )
            );
            return new EnhancementOutcome("DEGRADED", durationMs, new SearchAiEnhancer.SearchAiEnhancementResult(Map.of(), Map.of()));
        }
    }

    private String resolveRelationReason(SearchRelatedMatchView view, SearchAiEnhancer.SearchAiEnhancementResult result) {
        SearchAiEnhancer.RelatedEnhancement enhancement = result.relatedByNoteId().get(view.noteId());
        String candidate = enhancement == null ? null : enhancement.relationReason();
        return isMeaningfulRelationReason(candidate) ? candidate.trim() : view.relationReason();
    }

    private ExternalSupplementView toExternalSupplementView(ExternalSupplementSeed seed, SearchAiEnhancer.SearchAiEnhancementResult result) {
        SearchAiEnhancer.ExternalEnhancement enhancement = result.externalBySourceUri().get(seed.sourceUri());
        String relationLabel = sanitizeRelationLabel(enhancement == null ? null : enhancement.relationLabel(), seed.fallbackRelationLabel());
        List<String> keywords = sanitizeKeywords(enhancement == null ? List.of() : enhancement.keywords(), seed.keywords());
        String summarySnippet = sanitizeSummarySnippet(enhancement == null ? null : enhancement.summarySnippet(), seed.fallbackSummarySnippet());
        return new ExternalSupplementView(
            seed.sourceName(),
            seed.sourceUri(),
            seed.summary(),
            keywords,
            seed.relationTags(),
            relationLabel,
            summarySnippet
        );
    }

    private String descriptiveRelationReason(FieldOverlap overlap) {
        String sharedTerms = String.join("、", overlap.sharedTokens());
        return switch (overlap.field()) {
            case TITLE -> "共享标题主题：" + sharedTerms;
            case SUMMARY -> "共享摘要主题：" + sharedTerms;
            case KEY_POINTS -> "共享关键点：" + sharedTerms;
            case TAGS -> "共享标签：" + sharedTerms;
            case LATEST_CONTENT -> "共享内容线索：" + sharedTerms;
        };
    }

    private boolean isMeaningfulRelationReason(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        return !normalized.contains("语义相关")
            && !normalized.contains("相关内容")
            && !normalized.contains("相关主题")
            && normalized.length() <= 48;
    }

    private String sanitizeRelationLabel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return List.of("可能更新", "可能冲突", "背景补充", "延伸阅读").contains(normalized) ? normalized : fallback;
    }

    private List<String> sanitizeKeywords(List<String> value, List<String> fallback) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (value != null) {
            value.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .limit(4)
                .forEach(normalized::add);
        }
        return normalized.isEmpty() ? fallback : normalized.stream().toList();
    }

    private String sanitizeSummarySnippet(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 140 ? normalized : normalized.substring(0, 140) + "...";
    }

    private List<String> buildKeywords(String query) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>(tokenize(query));
        if (keywords.isEmpty()) {
            keywords.add(query.trim());
        }
        return keywords.stream().limit(3).toList();
    }

    private Set<String> tokenize(String rawValue) {
        String normalized = normalizeForMatch(rawValue);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean containsNormalized(String rawValue, String normalizedQuery) {
        String normalizedField = normalizeForMatch(rawValue);
        return !normalizedField.isBlank() && normalizedField.contains(normalizedQuery);
    }

    private String normalizeForMatch(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        return rawValue
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String requireQuery(UUID userId, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            log.warn("module=SearchApplicationService action=search_query_rejected user_id={} error_code=INVALID_SEARCH_QUERY error_message=query must not be blank trace_id=null",
                userId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must not be blank");
        }
        return rawValue.trim();
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private int toLatencyMs(long durationMs) {
        if (durationMs > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) durationMs;
    }

    private record ScoredExactMatch(SearchExactMatchView view, int score) {
    }

    private record ScoredRelatedMatch(SearchRelatedMatchView view, SearchRepository.SearchCandidate candidate, int score) {
    }

    private record FieldOverlap(MatchField field, List<String> sharedTokens, int sharedTokenCount, int priority) {
    }

    private record ExternalSupplementSeed(
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        List<String> relationTags,
        String fallbackRelationLabel,
        String fallbackSummarySnippet
    ) {
    }

    private record EnhancementOutcome(
        String status,
        long durationMs,
        SearchAiEnhancer.SearchAiEnhancementResult result
    ) {
    }

    private enum MatchField {
        TITLE,
        SUMMARY,
        KEY_POINTS,
        TAGS,
        LATEST_CONTENT
    }

    public record SearchView(
        String query,
        List<SearchExactMatchView> exactMatches,
        List<SearchRelatedMatchView> relatedMatches,
        List<ExternalSupplementView> externalSupplements
    ) {
    }

    public record SearchExactMatchView(
        UUID noteId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        String latestContent,
        Instant updatedAt
    ) {
    }

    public record SearchRelatedMatchView(
        UUID noteId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        String latestContent,
        String relationReason,
        Instant updatedAt
    ) {
        public SearchRelatedMatchView withRelationReason(String newRelationReason) {
            return new SearchRelatedMatchView(noteId, title, currentSummary, currentKeyPoints, latestContent, newRelationReason, updatedAt);
        }
    }

    public record ExternalSupplementView(
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        List<String> relationTags,
        String relationLabel,
        String summarySnippet
    ) {
    }

    private static final UserActionEventRepository NO_OP_USER_ACTION_EVENT_REPOSITORY = (userId, eventType, entityType, entityId, traceId, payload) -> {
    };

    private static final ToolInvocationLogRepository NO_OP_TOOL_INVOCATION_LOG_REPOSITORY = (userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage) -> {
    };

    private static final class NoOpAgentTraceRepository implements AgentTraceRepository {

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
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }
}
