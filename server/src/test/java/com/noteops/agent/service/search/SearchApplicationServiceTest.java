package com.noteops.agent.service.search;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.search.SearchRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        repository.store(candidate(userId, exactTitleId, "Kickoff alpha", "Old summary", List.of("owners aligned"), "phase one notes", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, exactSummaryId, "Project brief", "Kickoff alpha recap", List.of("owners aligned"), "phase two notes", Instant.parse("2026-03-16T02:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));
        repository.store(candidate(UUID.randomUUID(), UUID.randomUUID(), "Other user's note", "Kickoff alpha", List.of("ignore"), "ignore", Instant.parse("2026-03-16T04:00:00Z")));

        SearchApplicationService service = new SearchApplicationService(repository);
        SearchApplicationService.SearchView result = service.search(userId.toString(), "kickoff alpha");

        assertThat(result.query()).isEqualTo("kickoff alpha");
        assertThat(result.exactMatches()).extracting(SearchApplicationService.SearchExactMatchView::noteId)
            .containsExactly(exactTitleId, exactSummaryId);
        assertThat(result.relatedMatches()).extracting(SearchApplicationService.SearchRelatedMatchView::noteId)
            .containsExactly(relatedId);
        assertThat(result.relatedMatches().getFirst().relationReason()).isEqualTo("TITLE_TOKEN_OVERLAP");
        assertThat(result.exactMatches()).extracting(SearchApplicationService.SearchExactMatchView::noteId)
            .doesNotContain(relatedId);
        assertThat(result.externalSupplements()).hasSize(2);
        assertThat(result.externalSupplements().getFirst().sourceName()).isEqualTo("Search Stub Background");
        assertThat(result.externalSupplements().getFirst().keywords()).containsExactly("kickoff", "alpha");
        assertThat(result.externalSupplements().getFirst().relationTags()).containsExactly("BACKGROUND");
    }

    @Test
    void recordsGovernanceArtifactsForSearch() {
        UUID userId = UUID.randomUUID();
        UUID exactTitleId = UUID.randomUUID();
        UUID exactSummaryId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();

        InMemorySearchRepository repository = new InMemorySearchRepository();
        repository.store(candidate(userId, exactTitleId, "Kickoff alpha", "Old summary", List.of("owners aligned"), "phase one notes", Instant.parse("2026-03-16T01:00:00Z")));
        repository.store(candidate(userId, exactSummaryId, "Project brief", "Kickoff alpha recap", List.of("owners aligned"), "phase two notes", Instant.parse("2026-03-16T02:00:00Z")));
        repository.store(candidate(userId, relatedId, "Kickoff review", "Team coordination", List.of("alignment"), "phase follow-up", Instant.parse("2026-03-16T03:00:00Z")));

        RecordingAgentTraceRepository traceRepository = new RecordingAgentTraceRepository();
        RecordingToolInvocationLogRepository toolInvocationLogRepository = new RecordingToolInvocationLogRepository();
        RecordingUserActionEventRepository userActionEventRepository = new RecordingUserActionEventRepository();

        SearchApplicationService service = new SearchApplicationService(
            repository,
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
        assertThat(userActionEventRepository.events).hasSize(1);
        assertThat(userActionEventRepository.events.getFirst().eventType()).isEqualTo("SEARCH_EXTERNAL_SUPPLEMENTS_GENERATED");
        assertThat(userActionEventRepository.events.getFirst().entityType()).isEqualTo("SEARCH_QUERY");
        assertThat(userActionEventRepository.events.getFirst().traceId()).isEqualTo(traceRepository.traceId);
        assertThat(userActionEventRepository.events.getFirst().payload()).containsEntry("exact_count", 2);
        assertThat(userActionEventRepository.events.getFirst().payload()).containsEntry("related_count", 1);
        assertThat(userActionEventRepository.events.getFirst().payload()).containsEntry("external_count", 2);
        assertThat(toolInvocationLogRepository.logs).hasSize(1);
        assertThat(toolInvocationLogRepository.logs.getFirst().toolName()).isEqualTo("search.external-supplement-stub");
        assertThat(toolInvocationLogRepository.logs.getFirst().status()).isEqualTo("COMPLETED");
        assertThat(toolInvocationLogRepository.logs.getFirst().inputDigest()).containsEntry("query", "kickoff alpha");
        assertThat(toolInvocationLogRepository.logs.getFirst().outputDigest()).containsEntry("external_count", 2);
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
                                                       String latestContent,
                                                       Instant updatedAt) {
        return new SearchRepository.SearchCandidate(noteId, userId, title, currentSummary, currentKeyPoints, latestContent, updatedAt);
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
