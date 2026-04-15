package com.noteops.agent.service.preference;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPreferenceProfileApplicationServiceTest {

    @Test
    void createsPreferenceProfileAndWritesTrace() {
        UUID userId = UUID.randomUUID();
        InMemoryUserPreferenceProfileRepository repository = new InMemoryUserPreferenceProfileRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        UserPreferenceProfileApplicationService service = newService(repository, traceRepository);

        UserPreferenceProfileApplicationService.UserPreferenceProfileCommandResult result = service.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                userId.toString(),
                new InterestProfile(
                    List.of("agents", "agents", "tooling"),
                    List.of("crypto"),
                    Map.of("HN", 0.8),
                    Map.of("save_as_note", 0.9),
                    Map.of("review", 1.0)
                )
            )
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.profile().userId()).isEqualTo(userId);
        assertThat(result.profile().interestProfile().preferredTopics()).containsExactly("agents", "tooling");
        assertThat(result.profile().interestProfile().sourceWeights()).containsEntry("HN", 0.8);
        assertThat(traceRepository.entryTypes).contains("USER_PREFERENCE_PROFILE_UPSERT");
        assertThat(traceRepository.completedTraceIds).hasSize(1);
    }

    @Test
    void updatesExistingPreferenceProfileWithoutChangingId() {
        UUID userId = UUID.randomUUID();
        InMemoryUserPreferenceProfileRepository repository = new InMemoryUserPreferenceProfileRepository();
        UserPreferenceProfileApplicationService service = newService(repository, new InMemoryAgentTraceRepository());

        UserPreferenceProfileApplicationService.UserPreferenceProfileView created = service.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                userId.toString(),
                new InterestProfile(List.of("agents"), List.of(), Map.of("HN", 0.8), Map.of(), Map.of())
            )
        ).profile();

        UserPreferenceProfileApplicationService.UserPreferenceProfileView updated = service.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                userId.toString(),
                new InterestProfile(List.of("search"), List.of("crypto"), Map.of("GITHUB", 1.0), Map.of(), Map.of("idea_followup", 0.7))
            )
        ).profile();

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.interestProfile().preferredTopics()).containsExactly("search");
        assertThat(updated.interestProfile().ignoredTopics()).containsExactly("crypto");
        assertThat(updated.interestProfile().sourceWeights()).containsEntry("GITHUB", 1.0);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    void loadsExistingPreferenceProfile() {
        UUID userId = UUID.randomUUID();
        InMemoryUserPreferenceProfileRepository repository = new InMemoryUserPreferenceProfileRepository();
        UserPreferenceProfileApplicationService service = newService(repository, new InMemoryAgentTraceRepository());
        service.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                userId.toString(),
                new InterestProfile(List.of("agents"), List.of(), Map.of("HN", 0.8), Map.of(), Map.of())
            )
        );

        UserPreferenceProfileApplicationService.UserPreferenceProfileView loaded = service.get(userId.toString());

        assertThat(loaded.interestProfile().preferredTopics()).containsExactly("agents");
    }

    @Test
    void rejectsMissingPreferenceProfile() {
        UserPreferenceProfileApplicationService service = newService(
            new InMemoryUserPreferenceProfileRepository(),
            new InMemoryAgentTraceRepository()
        );

        assertThatThrownBy(() -> service.get(UUID.randomUUID().toString()))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).errorCode())
            .isEqualTo("USER_PREFERENCE_PROFILE_NOT_FOUND");
    }

    @Test
    void rejectsInvalidUserId() {
        UserPreferenceProfileApplicationService service = newService(
            new InMemoryUserPreferenceProfileRepository(),
            new InMemoryAgentTraceRepository()
        );

        assertThatThrownBy(() -> service.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                "bad-user-id",
                InterestProfile.empty()
            )
        ))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).errorCode())
            .isEqualTo("INVALID_USER_ID");
    }

    private UserPreferenceProfileApplicationService newService(InMemoryUserPreferenceProfileRepository repository,
                                                              InMemoryAgentTraceRepository traceRepository) {
        return new UserPreferenceProfileApplicationService(repository, new UserPreferenceProfileTraceService(traceRepository));
    }

    private static final class InMemoryUserPreferenceProfileRepository implements UserPreferenceProfileRepository {

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

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        private final List<String> entryTypes = new ArrayList<>();
        private final List<UUID> completedTraceIds = new ArrayList<>();

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            entryTypes.add(entryType);
            return UUID.randomUUID();
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
            completedTraceIds.add(traceId);
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }
}
