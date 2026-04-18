package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import com.noteops.agent.service.preference.PreferenceContextInjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendInboxQueryServiceTest {

    @Mock
    private TrendItemRepository trendItemRepository;

    @Test
    void rejectsInvalidUserIdWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            "bad-user-id",
            null,
            null,
            "trace-1"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_USER_ID");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-1");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("user_id must be a valid UUID");
            });
    }

    @Test
    void rejectsInvalidStatusWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            "NOT_A_STATUS",
            null,
            "trace-2"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_TREND_STATUS");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-2");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("status must be a valid trend item status");
            });
    }

    @Test
    void rejectsInvalidSourceTypeWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            TrendItemStatus.ANALYZED.name(),
            "NOT_A_SOURCE",
            "trace-3"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_TREND_SOURCE_TYPE");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-3");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("source_type must be a valid trend source type");
            });
    }

    @Test
    void wrapsUnexpectedFailuresWithStableClientMessage() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);
        when(trendItemRepository.findInboxByUserId(any(UUID.class), any(TrendItemStatus.class), any(TrendSourceType.class)))
            .thenThrow(new RuntimeException("db password leaked"));

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            TrendItemStatus.ANALYZED.name(),
            TrendSourceType.HN.name(),
            "trace-4"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("TREND_INBOX_QUERY_FAILED");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-4");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("trend inbox query failed");
            });
    }

    @Test
    void reordersTrendItemsWhenPreferenceProfileExists() {
        UUID userId = UUID.randomUUID();
        InMemoryUserPreferenceProfileRepository preferenceRepository = new InMemoryUserPreferenceProfileRepository();
        preferenceRepository.put(userId, new InterestProfile(
            List.of("agents"),
            List.of(),
            Map.of("GITHUB", 1.0),
            Map.of("save_as_note", 0.8),
            Map.of()
        ));
        TrendInboxQueryService service = new TrendInboxQueryService(
            trendItemRepository,
            new PreferenceContextInjectionService(preferenceRepository)
        );

        TrendItemRepository.TrendItemRecord hnRecord = trendRecord(
            userId,
            TrendSourceType.HN,
            95d,
            List.of("crypto"),
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-16T03:00:00Z")
        );
        TrendItemRepository.TrendItemRecord githubRecord = trendRecord(
            userId,
            TrendSourceType.GITHUB,
            90d,
            List.of("agents"),
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-16T02:00:00Z")
        );
        when(trendItemRepository.findInboxByUserId(userId, TrendItemStatus.ANALYZED, null))
            .thenReturn(List.of(hnRecord, githubRecord));

        List<com.noteops.agent.dto.trend.TrendInboxItemResponse> result = service.list(
            new TrendInboxQueryService.InboxQueryCommand(userId.toString(), TrendItemStatus.ANALYZED.name(), null, "trace-pref-order")
        );

        assertThat(result).extracting(com.noteops.agent.dto.trend.TrendInboxItemResponse::trendItemId)
            .containsExactly(githubRecord.id().toString(), hnRecord.id().toString());
    }

    @Test
    void overridesSuggestedActionToIgnoreWhenIgnoredTopicSignalDominates() {
        UUID userId = UUID.randomUUID();
        InMemoryUserPreferenceProfileRepository preferenceRepository = new InMemoryUserPreferenceProfileRepository();
        preferenceRepository.put(userId, new InterestProfile(
            List.of(),
            List.of("crypto"),
            Map.of(),
            Map.of(
                "ignore_trend", 0.9,
                "save_as_note", 0.1,
                "promote_to_idea", 0.1
            ),
            Map.of()
        ));
        TrendInboxQueryService service = new TrendInboxQueryService(
            trendItemRepository,
            new PreferenceContextInjectionService(preferenceRepository)
        );

        TrendItemRepository.TrendItemRecord trend = trendRecord(
            userId,
            TrendSourceType.HN,
            88d,
            List.of("crypto"),
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-16T01:00:00Z")
        );
        when(trendItemRepository.findInboxByUserId(userId, TrendItemStatus.ANALYZED, null))
            .thenReturn(List.of(trend));

        List<com.noteops.agent.dto.trend.TrendInboxItemResponse> result = service.list(
            new TrendInboxQueryService.InboxQueryCommand(userId.toString(), TrendItemStatus.ANALYZED.name(), null, "trace-pref-ignore")
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().suggestedAction()).isEqualTo("IGNORE");
    }

    @Test
    void keepsOriginalOrderingWhenPreferenceProfileIsMissing() {
        UUID userId = UUID.randomUUID();
        TrendInboxQueryService service = new TrendInboxQueryService(
            trendItemRepository,
            new PreferenceContextInjectionService(new InMemoryUserPreferenceProfileRepository())
        );

        TrendItemRepository.TrendItemRecord first = trendRecord(
            userId,
            TrendSourceType.HN,
            91d,
            List.of("infra"),
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-16T05:00:00Z")
        );
        TrendItemRepository.TrendItemRecord second = trendRecord(
            userId,
            TrendSourceType.GITHUB,
            99d,
            List.of("agents"),
            TrendActionType.PROMOTE_TO_IDEA,
            Instant.parse("2026-04-16T04:00:00Z")
        );
        when(trendItemRepository.findInboxByUserId(userId, TrendItemStatus.ANALYZED, null))
            .thenReturn(List.of(first, second));

        List<com.noteops.agent.dto.trend.TrendInboxItemResponse> result = service.list(
            new TrendInboxQueryService.InboxQueryCommand(userId.toString(), TrendItemStatus.ANALYZED.name(), null, "trace-pref-empty")
        );

        assertThat(result).extracting(com.noteops.agent.dto.trend.TrendInboxItemResponse::trendItemId)
            .containsExactly(first.id().toString(), second.id().toString());
        assertThat(result).extracting(com.noteops.agent.dto.trend.TrendInboxItemResponse::suggestedAction)
            .containsExactly("SAVE_AS_NOTE", "PROMOTE_TO_IDEA");
    }

    @Test
    void degradesToEmptyPreferenceContextWhenProfileLoadFails() {
        UUID userId = UUID.randomUUID();
        TrendInboxQueryService service = new TrendInboxQueryService(
            trendItemRepository,
            new PreferenceContextInjectionService(new ThrowingUserPreferenceProfileRepository())
        );

        TrendItemRepository.TrendItemRecord first = trendRecord(
            userId,
            TrendSourceType.HN,
            91d,
            List.of("infra"),
            TrendActionType.SAVE_AS_NOTE,
            Instant.parse("2026-04-16T05:00:00Z")
        );
        TrendItemRepository.TrendItemRecord second = trendRecord(
            userId,
            TrendSourceType.GITHUB,
            99d,
            List.of("agents"),
            TrendActionType.PROMOTE_TO_IDEA,
            Instant.parse("2026-04-16T04:00:00Z")
        );
        when(trendItemRepository.findInboxByUserId(userId, TrendItemStatus.ANALYZED, null))
            .thenReturn(List.of(first, second));

        List<com.noteops.agent.dto.trend.TrendInboxItemResponse> result = service.list(
            new TrendInboxQueryService.InboxQueryCommand(userId.toString(), TrendItemStatus.ANALYZED.name(), null, "trace-pref-degraded")
        );

        assertThat(result).extracting(com.noteops.agent.dto.trend.TrendInboxItemResponse::trendItemId)
            .containsExactly(first.id().toString(), second.id().toString());
        assertThat(result).extracting(com.noteops.agent.dto.trend.TrendInboxItemResponse::suggestedAction)
            .containsExactly("SAVE_AS_NOTE", "PROMOTE_TO_IDEA");
    }

    private TrendItemRepository.TrendItemRecord trendRecord(UUID userId,
                                                            TrendSourceType sourceType,
                                                            double normalizedScore,
                                                            List<String> topicTags,
                                                            TrendActionType suggestedAction,
                                                            Instant updatedAt) {
        UUID trendItemId = UUID.randomUUID();
        return new TrendItemRepository.TrendItemRecord(
            trendItemId,
            userId,
            sourceType,
            sourceType.name().toLowerCase() + "-" + trendItemId,
            "Trend title " + trendItemId,
            "https://example.com/trend/" + trendItemId,
            "Trend summary",
            normalizedScore,
            new TrendAnalysisPayload(
                "Trend summary",
                "why it matters",
                topicTags,
                "DISCUSSION",
                true,
                true,
                suggestedAction,
                "reasoning"
            ),
            Map.of(),
            TrendItemStatus.ANALYZED,
            suggestedAction,
            updatedAt.minusSeconds(600),
            updatedAt.minusSeconds(300),
            null,
            null,
            updatedAt.minusSeconds(1_200),
            updatedAt
        );
    }

    private static final class InMemoryUserPreferenceProfileRepository implements UserPreferenceProfileRepository {

        private final Map<UUID, UserPreferenceProfileRecord> records = new LinkedHashMap<>();

        @Override
        public Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
            return Optional.ofNullable(records.get(userId));
        }

        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            UserPreferenceProfileRecord existing = records.get(userId);
            Instant now = Instant.now();
            UserPreferenceProfileRecord stored = existing == null
                ? new UserPreferenceProfileRecord(UUID.randomUUID(), userId, interestProfile, now, now)
                : new UserPreferenceProfileRecord(existing.id(), userId, interestProfile, existing.createdAt(), now);
            records.put(userId, stored);
            return stored;
        }

        private void put(UUID userId, InterestProfile interestProfile) {
            Instant now = Instant.parse("2026-04-16T00:00:00Z");
            records.put(userId, new UserPreferenceProfileRecord(UUID.randomUUID(), userId, interestProfile, now, now));
        }
    }

    private static final class ThrowingUserPreferenceProfileRepository implements UserPreferenceProfileRepository {
        @Override
        public Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
            throw new RuntimeException("simulated preference repository failure");
        }

        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            throw new UnsupportedOperationException("not needed");
        }
    }
}
