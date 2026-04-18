package com.noteops.agent.service.preference;

import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPreferenceProfileRecomputeServiceTest {

    @Test
    void recomputesInterestProfileFromTrendReviewAndTaskSignals() {
        UUID userId = UUID.randomUUID();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        InMemoryUserPreferenceProfileRepository profileRepository = new InMemoryUserPreferenceProfileRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        UserPreferenceProfileRecomputeService service = newService(eventRepository, profileRepository, traceRepository);

        eventRepository.events.add(event(userId, "TREND_SAVED_AS_NOTE", Map.of(
            "source_type", "HN",
            "topic_tags", List.of("agents", "ai")
        ), Instant.parse("2026-04-15T10:00:00Z")));
        eventRepository.events.add(event(userId, "TREND_PROMOTED_TO_IDEA", Map.of(
            "source_type", "GITHUB",
            "analysis_topic_tags", List.of("agents", "automation")
        ), Instant.parse("2026-04-15T09:00:00Z")));
        eventRepository.events.add(event(userId, "TREND_ITEM_IGNORED", Map.of(
            "source_type", "HN",
            "topic_tags", List.of("crypto")
        ), Instant.parse("2026-04-15T08:00:00Z")));
        eventRepository.events.add(event(userId, "REVIEW_COMPLETED", Map.of(), Instant.parse("2026-04-15T07:00:00Z")));
        eventRepository.events.add(event(userId, "REVIEW_NOT_STARTED", Map.of(), Instant.parse("2026-04-15T06:00:00Z")));
        eventRepository.events.add(event(userId, "TASK_COMPLETED", Map.of(
            "task_type", "IDEA_NEXT_ACTION"
        ), Instant.parse("2026-04-15T05:00:00Z")));
        eventRepository.events.add(event(userId, "TASK_SKIPPED", Map.of(
            "task_type", "REVIEW_FOLLOW_UP"
        ), Instant.parse("2026-04-15T04:00:00Z")));

        UserPreferenceProfileRecomputeService.RecomputeCommandResult result = service.recompute(
            new UserPreferenceProfileRecomputeService.RecomputeCommand(userId.toString())
        );

        InterestProfile profile = result.profile().interestProfile();
        assertThat(result.traceId()).isNotBlank();
        assertThat(result.processedEventCount()).isEqualTo(7);
        assertThat(profile.preferredTopics()).contains("agents", "automation", "ai");
        assertThat(profile.ignoredTopics()).containsExactly("crypto");
        assertThat(profile.sourceWeights()).containsEntry("HN", 0.5).containsEntry("GITHUB", 1.0);
        assertThat(profile.actionBias())
            .containsEntry("save_as_note", 1d / 3d)
            .containsEntry("promote_to_idea", 1d / 3d)
            .containsEntry("ignore_trend", 1d / 3d);
        assertThat(profile.taskBias())
            .containsEntry("review", 1d / 3d)
            .containsEntry("idea_followup", 1.0);
        assertThat(traceRepository.completedTraceIds).hasSize(1);
    }

    @Test
    void persistsEmptyProfileWhenNoSupportedSignalsExist() {
        UUID userId = UUID.randomUUID();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        InMemoryUserPreferenceProfileRepository profileRepository = new InMemoryUserPreferenceProfileRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        UserPreferenceProfileRecomputeService service = newService(eventRepository, profileRepository, traceRepository);

        eventRepository.events.add(event(userId, "CHANGE_PROPOSAL_APPLIED", Map.of(), Instant.parse("2026-04-15T10:00:00Z")));

        UserPreferenceProfileRecomputeService.RecomputeCommandResult result = service.recompute(
            new UserPreferenceProfileRecomputeService.RecomputeCommand(userId.toString())
        );

        assertThat(result.profile().interestProfile()).isEqualTo(InterestProfile.empty());
        assertThat(traceRepository.completedTraceIds).hasSize(1);
    }

    @Test
    void doesNotWriteIdeaFollowupTaskBiasWhenOnlyReviewSignalsExist() {
        UUID userId = UUID.randomUUID();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        InMemoryUserPreferenceProfileRepository profileRepository = new InMemoryUserPreferenceProfileRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        UserPreferenceProfileRecomputeService service = newService(eventRepository, profileRepository, traceRepository);

        eventRepository.events.add(event(userId, "REVIEW_COMPLETED", Map.of(), Instant.parse("2026-04-15T10:00:00Z")));
        eventRepository.events.add(event(userId, "REVIEW_NOT_STARTED", Map.of(), Instant.parse("2026-04-15T09:00:00Z")));

        UserPreferenceProfileRecomputeService.RecomputeCommandResult result = service.recompute(
            new UserPreferenceProfileRecomputeService.RecomputeCommand(userId.toString())
        );

        assertThat(result.profile().interestProfile().taskBias())
            .containsEntry("review", 0.5)
            .doesNotContainKey("idea_followup");
    }

    @Test
    void marksFailedTraceWhenProfilePersistenceFails() {
        UUID userId = UUID.randomUUID();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        FailingUserPreferenceProfileRepository profileRepository = new FailingUserPreferenceProfileRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        UserPreferenceProfileRecomputeService service = newService(eventRepository, profileRepository, traceRepository);

        eventRepository.events.add(event(userId, "TREND_SAVED_AS_NOTE", Map.of(
            "source_type", "HN",
            "topic_tags", List.of("agents")
        ), Instant.parse("2026-04-15T10:00:00Z")));

        assertThatThrownBy(() -> service.recompute(new UserPreferenceProfileRecomputeService.RecomputeCommand(userId.toString())))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated recompute persistence failure");

        assertThat(traceRepository.failedTraceIds).hasSize(1);
        assertThat(traceRepository.failedOrchestratorStates.values())
            .anySatisfy(state -> assertThat(state)
                .containsEntry("result", "FAILED")
                .containsEntry("error_code", "PREFERENCE_RECOMPUTE_FAILED"));
    }

    private UserPreferenceProfileRecomputeService newService(InMemoryUserActionEventRepository eventRepository,
                                                             UserPreferenceProfileRepository profileRepository,
                                                             InMemoryAgentTraceRepository traceRepository) {
        return new UserPreferenceProfileRecomputeService(
            eventRepository,
            profileRepository,
            new UserPreferenceProfileTraceService(traceRepository)
        );
    }

    private UserActionEventRepository.UserActionEventRecord event(UUID userId,
                                                                  String eventType,
                                                                  Map<String, Object> payload,
                                                                  Instant createdAt) {
        return new UserActionEventRepository.UserActionEventRecord(
            UUID.randomUUID(),
            userId,
            eventType,
            "TEST_ENTITY",
            null,
            UUID.randomUUID(),
            payload,
            createdAt
        );
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {
        private final List<UserActionEventRecord> events = new ArrayList<>();

        @Override
        public void append(UUID userId,
                           String eventType,
                           String entityType,
                           UUID entityId,
                           UUID traceId,
                           Map<String, Object> payload) {
        }

        @Override
        public List<UserActionEventRecord> findRecentByUserId(UUID userId, int limit) {
            return events.stream()
                .filter(event -> event.userId().equals(userId))
                .sorted(Comparator.comparing(UserActionEventRecord::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
        }
    }

    private static class InMemoryUserPreferenceProfileRepository implements UserPreferenceProfileRepository {

        private final Map<UUID, UserPreferenceProfileRecord> profilesByUserId = new LinkedHashMap<>();

        @Override
        public Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
            return Optional.ofNullable(profilesByUserId.get(userId));
        }

        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            UserPreferenceProfileRecord existing = profilesByUserId.get(userId);
            Instant now = Instant.now();
            UserPreferenceProfileRecord stored = existing == null
                ? new UserPreferenceProfileRecord(UUID.randomUUID(), userId, interestProfile, now, now)
                : new UserPreferenceProfileRecord(existing.id(), userId, interestProfile, existing.createdAt(), now);
            profilesByUserId.put(userId, stored);
            return stored;
        }
    }

    private static final class FailingUserPreferenceProfileRepository extends InMemoryUserPreferenceProfileRepository {
        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            throw new RuntimeException("simulated recompute persistence failure");
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {
        private final List<UUID> completedTraceIds = new ArrayList<>();
        private final List<UUID> failedTraceIds = new ArrayList<>();
        private final Map<UUID, Map<String, Object>> failedOrchestratorStates = new LinkedHashMap<>();

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
            completedTraceIds.add(traceId);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            failedTraceIds.add(traceId);
            failedOrchestratorStates.put(traceId, orchestratorState);
        }
    }
}
