package com.noteops.agent.service.search;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.search.SearchRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchApplicationServiceTest {

    @Test
    void returnsExactRelatedAndExternalBucketsWithStableOrdering() {
        UUID userId = UUID.randomUUID();
        UUID exactTitleId = UUID.randomUUID();
        UUID exactSummaryId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(userId, exactTitleId, "Kickoff alpha", "Old summary", List.of("owners aligned"), List.of("planning"), "phase one notes", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, exactSummaryId, "Project brief", "Kickoff alpha recap", List.of("owners aligned"), List.of("planning"), "phase two notes", Instant.parse("2026-03-16T02:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), List.of("kickoff"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));
        repository.store(candidate(UUID.randomUUID(), UUID.randomUUID(), "Other user's note", "Kickoff alpha", List.of("ignore"), List.of("ignore"), "ignore", Instant.parse("2026-03-16T04:00:00Z")));

        SearchApplicationService service = new SearchApplicationService(repository);
        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.query()).isEqualTo("kickoff alpha");
        assertThat(result.exactMatches()).extracting(SearchApplicationService.SearchExactMatchView::noteId)
            .containsExactly(exactTitleId, exactSummaryId);
        assertThat(result.relatedMatches()).extracting(SearchApplicationService.SearchRelatedMatchView::noteId)
            .containsExactly(relatedId);
        assertThat(result.relatedMatches().getFirst().relationReason()).startsWith("共享");
        assertThat(result.relatedMatches().getFirst().aiEnhanced()).isFalse();
        assertThat(result.exactMatches()).extracting(SearchApplicationService.SearchExactMatchView::noteId)
            .doesNotContain(relatedId);
        assertThat(result.externalSupplements()).hasSize(2);
        assertThat(result.externalSupplements().getFirst().sourceName()).isEqualTo("Search Stub Background");
        assertThat(result.externalSupplements().getFirst().keywords()).containsExactly("kickoff", "alpha");
        assertThat(result.externalSupplements().getFirst().relationTags()).containsExactly("BACKGROUND");
        assertThat(result.externalSupplements().getFirst().relationLabel()).isEqualTo("背景补充");
        assertThat(result.externalSupplements().getFirst().summarySnippet()).isNotBlank();
        assertThat(result.externalSupplements().getFirst().aiEnhanced()).isFalse();
        assertThat(result.aiEnhancementStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void appliesAiEnhancementWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(userId, UUID.randomUUID(), "Kickoff alpha", "Exact summary", List.of("owners aligned"), List.of("planning"), "alpha details", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), List.of("kickoff"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));

        SearchAiEnhancer enhancer = request -> new SearchAiEnhancer.SearchAiEnhancementResult(
            Map.of(relatedId, new SearchAiEnhancer.RelatedEnhancement("共享工作流链路：kickoff review")),
            Map.of(
                "stub://search/background?q=kickoff+alpha",
                new SearchAiEnhancer.ExternalEnhancement("可能更新", List.of("kickoff", "alpha", "review"), "外部线索提示 kickoff 计划有新增进展")
            )
        );

        SearchApplicationService service = new SearchApplicationService(
            repository,
            enhancer,
            new RecordingAgentTraceRepository(),
            new RecordingToolInvocationLogRepository(),
            new RecordingUserActionEventRepository()
        );

        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.relatedMatches().getFirst().relationReason()).isEqualTo("共享工作流链路：kickoff review");
        assertThat(result.relatedMatches().getFirst().aiEnhanced()).isTrue();
        assertThat(result.externalSupplements().getFirst().relationLabel()).isEqualTo("可能更新");
        assertThat(result.externalSupplements().getFirst().keywords()).containsExactly("kickoff", "alpha", "review");
        assertThat(result.externalSupplements().getFirst().summarySnippet()).isEqualTo("外部线索提示 kickoff 计划有新增进展");
        assertThat(result.externalSupplements().getFirst().aiEnhanced()).isTrue();
        assertThat(result.aiEnhancementStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void degradesGracefullyWhenAiEnhancementFails() {
        UUID userId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(userId, UUID.randomUUID(), "Kickoff alpha", "Exact summary", List.of("owners aligned"), List.of("planning"), "alpha details", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), List.of("kickoff"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));

        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();

        SearchApplicationService service = new SearchApplicationService(
            repository,
            request -> {
                throw new RuntimeException("simulated ai failure");
            },
            traceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.relatedMatches().getFirst().relationReason()).startsWith("共享");
        assertThat(result.relatedMatches().getFirst().aiEnhanced()).isFalse();
        assertThat(result.externalSupplements().getFirst().relationLabel()).isEqualTo("背景补充");
        assertThat(result.externalSupplements().getFirst().aiEnhanced()).isFalse();
        assertThat(result.aiEnhancementStatus()).isEqualTo("DEGRADED");
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("ai_enhancement_status", "DEGRADED");
        assertThat(userActionEventRepository.events).extracting(UserActionEventRecord::eventType)
            .contains("SEARCH_AI_ENHANCEMENT_DEGRADED", "SEARCH_EXECUTED", "SEARCH_EXTERNAL_SUPPLEMENTS_GENERATED");
        assertThat(toolInvocationLogRepository.logs).extracting(ToolInvocationLogRecord::toolName)
            .contains("search.ai-enhancement", "search.execute", "search.external-supplement-generator");
        ToolInvocationLogRecord aiLog = toolInvocationLogRepository.logs.stream()
            .filter(log -> log.toolName().equals("search.ai-enhancement"))
            .findFirst()
            .orElseThrow();
        assertThat(aiLog.status()).isEqualTo("FAILED");
    }

    @Test
    void recordsGovernanceArtifactsForSearch() {
        UUID userId = UUID.randomUUID();
        UUID exactTitleId = UUID.randomUUID();
        UUID exactSummaryId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(userId, exactTitleId, "Kickoff alpha", "Old summary", List.of("owners aligned"), List.of("planning"), "phase one notes", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, exactSummaryId, "Project brief", "Kickoff alpha recap", List.of("owners aligned"), List.of("planning"), "phase two notes", Instant.parse("2026-03-16T02:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), List.of("kickoff"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));

        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();

        SearchApplicationService service = new SearchApplicationService(
            repository,
            request -> new SearchAiEnhancer.SearchAiEnhancementResult(Map.of(), Map.of()),
            traceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.exactMatches()).hasSize(2);
        assertThat(traceRepository.created.entryType()).isEqualTo("SEARCH_QUERY");
        assertThat(traceRepository.created.rootEntityType()).isEqualTo("SEARCH_QUERY");
        assertThat(traceRepository.created.workerSequence()).containsExactly("search-worker");
        assertThat(traceRepository.created.orchestratorState()).containsEntry("query", "kickoff alpha");
        assertThat(traceRepository.completed.traceId()).isEqualTo(traceRepository.traceId);
        assertThat(traceRepository.completed.resultSummary()).contains("2 exact matches", "1 related matches", "2 external supplements");
        assertThat(userActionEventRepository.events).extracting(UserActionEventRecord::eventType)
            .containsExactly("SEARCH_EXECUTED", "SEARCH_EXTERNAL_SUPPLEMENTS_GENERATED");
        assertThat(toolInvocationLogRepository.logs).extracting(ToolInvocationLogRecord::toolName)
            .containsExactly("search.ai-enhancement", "search.execute", "search.external-supplement-generator");
        assertThat(toolInvocationLogRepository.logs.get(1).outputDigest()).containsEntry("exact_count", 2);
        assertThat(result.aiEnhancementStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void capsAiRelatedCandidatesAtBudgetAndMixesRecencyWithForgettingSignals() {
        UUID userId = UUID.randomUUID();
        InMemorySearchRepository repository = new InMemorySearchRepository();
        Instant base = Instant.parse("2026-03-20T00:00:00Z");
        for (int index = 0; index < 30; index++) {
            repository.store(candidate(
                userId,
                UUID.randomUUID(),
                "Kickoff note " + index,
                "Team coordination summary " + index,
                List.of("alpha-" + index),
                List.of("kickoff"),
                "phase follow-up " + index,
                base.plusSeconds(index),
                base.plusSeconds(3_600L * (29 - index)),
                BigDecimal.valueOf(index)
            ));
        }

        CapturingSearchAiEnhancer enhancer = new CapturingSearchAiEnhancer();
        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();
        SearchApplicationService service = new SearchApplicationService(
            repository,
            enhancer,
            traceRepository,
            toolInvocationLogRepository,
            userActionEventRepository
        );

        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.relatedMatches()).hasSize(30);
        assertThat(enhancer.capturedRequest).isNotNull();
        assertThat(enhancer.capturedRequest.relatedCandidates()).hasSize(18);
        assertThat(enhancer.capturedRequest.externalCandidates()).hasSize(2);
        assertThat(enhancer.capturedRequest.relatedCandidates()).extracting(SearchAiEnhancer.RelatedCandidate::noteId)
            .doesNotHaveDuplicates();
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("selected_related_candidate_count", 18);
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("selected_related_by_recency_count", 9);
        assertThat(traceRepository.completed.orchestratorState()).containsEntry("selected_related_by_forgetting_count", 9);
    }

    @Test
    void matchesChineseQueryWhenCapturedContentContainsWhitespaceBetweenCjkCharacters() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(
            userId,
            noteId,
            "英文标题",
            "english summary only",
            List.of("english point"),
            List.of("english"),
            "这 是 一 段 测 试 内 容",
            Instant.parse("2026-03-16T03:00:00Z")
        ));

        SearchApplicationService service = new SearchApplicationService(repository);
        SearchApplicationService.SearchView result = service.search(userId.toString(), "测试");

        assertThat(result.exactMatches()).extracting(SearchApplicationService.SearchExactMatchView::noteId)
            .containsExactly(noteId);
    }

    @Test
    void rejectsBlankQuery() {
        SearchApplicationService service = new SearchApplicationService(new InMemorySearchRepository());

        assertThatThrownBy(() -> service.search(UUID.randomUUID().toString(), "   "))
            .isInstanceOf(ApiException.class)
            .hasMessage("query must not be blank");
    }

    @Test
    void rejectsInvalidUserId() {
        SearchApplicationService service = new SearchApplicationService(new InMemorySearchRepository());

        assertThatThrownBy(() -> service.search("bad-id", "kickoff"))
            .isInstanceOf(ApiException.class)
            .hasMessage("user_id must be a valid UUID");
    }

    private SearchRepository.SearchCandidate candidate(UUID userId,
                                                       UUID noteId,
                                                       String title,
                                                       String currentSummary,
                                                       List<String> currentKeyPoints,
                                                       List<String> currentTags,
                                                       String latestContent,
                                                       Instant updatedAt) {
        return candidate(userId, noteId, title, currentSummary, currentKeyPoints, currentTags, latestContent, updatedAt, null, null);
    }

    private SearchRepository.SearchCandidate candidate(UUID userId,
                                                       UUID noteId,
                                                       String title,
                                                       String currentSummary,
                                                       List<String> currentKeyPoints,
                                                       List<String> currentTags,
                                                       String latestContent,
                                                       Instant updatedAt,
                                                       Instant nextReviewAt,
                                                       BigDecimal masteryScore) {
        return new SearchRepository.SearchCandidate(
            noteId,
            userId,
            title,
            currentSummary,
            currentKeyPoints,
            currentTags,
            "stub://note/" + noteId,
            "CAPTURE_RAW",
            latestContent,
            updatedAt,
            nextReviewAt,
            masteryScore
        );
    }

    private static final class InMemorySearchRepository implements SearchRepository {

        private final Map<UUID, List<SearchCandidate>> candidatesByUser = new HashMap<>();

        void store(SearchCandidate candidate) {
            candidatesByUser.computeIfAbsent(candidate.userId(), ignored -> new ArrayList<>()).add(candidate);
        }

        @Override
        public List<SearchCandidate> findByUserId(UUID userId) {
            return candidatesByUser.getOrDefault(userId, List.of()).stream()
                .sorted(Comparator.comparing(SearchCandidate::updatedAt).reversed())
                .toList();
        }
    }

    private static final class RecordingAgentTraceRepository implements AgentTraceRepository {

        private final UUID traceId = UUID.randomUUID();
        private TraceCreateRecord created;
        private TraceCompletedRecord completed;

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            created = new TraceCreateRecord(userId, entryType, goal, rootEntityType, rootEntityId, workerSequence, orchestratorState);
            return traceId;
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completed = new TraceCompletedRecord(traceId, resultSummary, orchestratorState);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingUserActionEventRepository implements UserActionEventRepository {

        private final List<UserActionEventRecord> events = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            events.add(new UserActionEventRecord(userId, eventType, entityType, entityId, traceId, payload));
        }
    }

    private static final class RecordingToolInvocationLogRepository implements ToolInvocationLogRepository {

        private final List<ToolInvocationLogRecord> logs = new ArrayList<>();

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
            logs.add(new ToolInvocationLogRecord(userId, traceId, toolName, status, inputDigest, outputDigest, latencyMs, errorCode, errorMessage));
        }
    }

    private static final class CapturingSearchAiEnhancer implements SearchAiEnhancer {

        private SearchAiEnhancementRequest capturedRequest;

        @Override
        public SearchAiEnhancementResult enhance(SearchAiEnhancementRequest request) {
            capturedRequest = request;
            return new SearchAiEnhancementResult(Map.of(), Map.of());
        }
    }

    private record TraceCreateRecord(
        UUID userId,
        String entryType,
        String goal,
        String rootEntityType,
        UUID rootEntityId,
        List<String> workerSequence,
        Map<String, Object> orchestratorState
    ) {
    }

    private record TraceCompletedRecord(
        UUID traceId,
        String resultSummary,
        Map<String, Object> orchestratorState
    ) {
    }

    private record UserActionEventRecord(
        UUID userId,
        String eventType,
        String entityType,
        UUID entityId,
        UUID traceId,
        Map<String, Object> payload
    ) {
    }

    private record ToolInvocationLogRecord(
        UUID userId,
        UUID traceId,
        String toolName,
        String status,
        Map<String, Object> inputDigest,
        Map<String, Object> outputDigest,
        Integer latencyMs,
        String errorCode,
        String errorMessage
    ) {
    }
}
