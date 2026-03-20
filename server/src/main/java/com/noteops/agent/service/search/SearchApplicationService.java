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

    private final SearchRepository searchRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    @Autowired
    public SearchApplicationService(SearchRepository searchRepository,
                                    AgentTraceRepository agentTraceRepository,
                                    ToolInvocationLogRepository toolInvocationLogRepository,
                                    UserActionEventRepository userActionEventRepository) {
        this.searchRepository = searchRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    public SearchApplicationService(SearchRepository searchRepository) {
        this(
            searchRepository,
            new NoOpAgentTraceRepository(),
            NO_OP_TOOL_INVOCATION_LOG_REPOSITORY,
            NO_OP_USER_ACTION_EVENT_REPOSITORY
        );
    }

    @Transactional
    // 搜索入口：做 query 校验、候选评分、外部补充和 trace 写入。
    public SearchView search(String userIdRaw, String queryRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        String query = requireQuery(userId, queryRaw);
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isBlank()) {
            log.warn("module=SearchApplicationService action=search_query_rejected user_id={} error_code=INVALID_SEARCH_QUERY error_message=query must not be blank trace_id=null",
                userId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must not be blank");
        }

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
            relatedMatch(candidate, normalizedQuery).ifPresent(relatedMatches::add);
        }

        exactMatches.sort(Comparator
            .comparingInt(ScoredExactMatch::score).reversed()
            .thenComparing(match -> match.view().updatedAt(), Comparator.reverseOrder())
            .thenComparing(match -> match.view().noteId()));
        relatedMatches.sort(Comparator
            .comparingInt(ScoredRelatedMatch::score).reversed()
            .thenComparing(match -> match.view().updatedAt(), Comparator.reverseOrder())
            .thenComparing(match -> match.view().noteId()));

        List<ExternalSupplementView> externalSupplements = buildExternalSupplements(query);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        toolInvocationLogRepository.append(
            userId,
            traceId,
            "search.external-supplement-stub",
            "COMPLETED",
            Map.of(
                "query", query,
                "search_request_id", searchRequestId
            ),
            Map.of(
                "external_count", externalSupplements.size(),
                "source_names", externalSupplements.stream().map(ExternalSupplementView::sourceName).toList()
            ),
            toLatencyMs(durationMs),
            null,
            null
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
                "related_count", relatedMatches.size(),
                "external_count", externalSupplements.size()
            )
        );
        agentTraceRepository.markCompleted(
            traceId,
            "Search completed with " + exactMatches.size() + " exact matches, " + relatedMatches.size() + " related matches and " + externalSupplements.size() + " external supplements",
            Map.of(
                "search_request_id", searchRequestId,
                "query", query,
                "exact_count", exactMatches.size(),
                "related_count", relatedMatches.size(),
                "external_count", externalSupplements.size()
            )
        );
        log.info(
            "module=SearchApplicationService action=search_query_success path=/api/v1/search user_id={} search_request_id={} trace_id={} exact_count={} related_count={} external_count={} duration_ms={}",
            userId,
            searchRequestId,
            traceId,
            exactMatches.size(),
            relatedMatches.size(),
            externalSupplements.size(),
            durationMs
        );

        return new SearchView(
            query,
            exactMatches.stream().map(ScoredExactMatch::view).toList(),
            relatedMatches.stream().map(ScoredRelatedMatch::view).toList(),
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
            case KEY_POINTS -> 200;
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
        if (containsNormalized(candidate.latestContent(), normalizedQuery)) {
            return MatchField.LATEST_CONTENT;
        }
        return null;
    }

    private Optional<ScoredRelatedMatch> relatedMatch(SearchRepository.SearchCandidate candidate, String normalizedQuery) {
        Set<String> queryTokens = tokenize(normalizedQuery);
        if (queryTokens.isEmpty()) {
            return Optional.empty();
        }

        List<FieldOverlap> overlaps = List.of(
            overlap(candidate.title(), queryTokens, "TITLE_TOKEN_OVERLAP", 4),
            overlap(candidate.currentSummary(), queryTokens, "SUMMARY_TOKEN_OVERLAP", 3),
            overlap(String.join(" ", candidate.currentKeyPoints()), queryTokens, "KEYPOINT_TOKEN_OVERLAP", 2),
            overlap(candidate.latestContent(), queryTokens, "LATEST_CONTENT_TOKEN_OVERLAP", 1)
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
        return Optional.of(new ScoredRelatedMatch(toRelatedView(candidate, best.relationReason()), score));
    }

    private FieldOverlap overlap(String fieldValue, Set<String> queryTokens, String relationReason, int priority) {
        Set<String> fieldTokens = tokenize(fieldValue);
        fieldTokens.retainAll(queryTokens);
        return new FieldOverlap(relationReason, fieldTokens.size(), priority);
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

    private List<ExternalSupplementView> buildExternalSupplements(String query) {
        List<String> keywords = buildKeywords(query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return List.of(
            new ExternalSupplementView(
                "Search Stub Background",
                "stub://search/background?q=" + encodedQuery,
                "Background reading related to " + query,
                keywords,
                List.of("BACKGROUND")
            ),
            new ExternalSupplementView(
                "Search Stub Follow-up",
                "stub://search/follow-up?q=" + encodedQuery,
                "Follow-up reading related to " + query,
                keywords,
                List.of("FOLLOW_UP")
            )
        );
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

    private record ScoredRelatedMatch(SearchRelatedMatchView view, int score) {
    }

    private record FieldOverlap(String relationReason, int sharedTokenCount, int priority) {
    }

    private enum MatchField {
        TITLE,
        SUMMARY,
        KEY_POINTS,
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
    }

    public record ExternalSupplementView(
        String sourceName,
        String sourceUri,
        String summary,
        List<String> keywords,
        List<String> relationTags
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
