package com.noteops.agent.service.preference;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPreferenceProfileRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceProfileRecomputeService.class);
    private static final int EVENT_WINDOW_SIZE = 200;
    private static final int TOPIC_LIMIT = 8;
    private static final String DEFAULT_RECOMPUTE_ERROR_CODE = "PREFERENCE_RECOMPUTE_FAILED";

    private final UserActionEventRepository userActionEventRepository;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final UserPreferenceProfileTraceService userPreferenceProfileTraceService;

    public UserPreferenceProfileRecomputeService(UserActionEventRepository userActionEventRepository,
                                                 UserPreferenceProfileRepository userPreferenceProfileRepository,
                                                 UserPreferenceProfileTraceService userPreferenceProfileTraceService) {
        this.userActionEventRepository = userActionEventRepository;
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.userPreferenceProfileTraceService = userPreferenceProfileTraceService;
    }

    @Transactional
    public RecomputeCommandResult recompute(RecomputeCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        UUID traceId = userPreferenceProfileTraceService.createRecomputeTrace(userId, EVENT_WINDOW_SIZE);
        String traceIdText = traceId.toString();
        long startedAt = System.nanoTime();
        int processedEventCount = 0;
        log.info(
            "module=UserPreferenceProfileRecomputeService action=user_preference_profile_recompute_start trace_id={} user_id={} result=RUNNING event_window_size={}",
            traceIdText,
            userId,
            EVENT_WINDOW_SIZE
        );

        try {
            List<UserActionEventRepository.UserActionEventRecord> recentEvents =
                userActionEventRepository.findRecentByUserId(userId, EVENT_WINDOW_SIZE);
            processedEventCount = recentEvents.size();
            InterestProfile recomputedProfile = aggregateInterestProfile(recentEvents);
            UserPreferenceProfileRepository.UserPreferenceProfileRecord persisted =
                userPreferenceProfileRepository.upsert(userId, recomputedProfile);

            userPreferenceProfileTraceService.markRecomputeCompleted(
                traceId,
                userId,
                persisted.id(),
                persisted.interestProfile(),
                processedEventCount
            );

            int durationMs = durationMs(startedAt);
            log.info(
                "module=UserPreferenceProfileRecomputeService action=user_preference_profile_recompute_success trace_id={} user_id={} profile_id={} processed_event_count={} preferred_topics_count={} ignored_topics_count={} result=COMPLETED duration_ms={}",
                traceIdText,
                userId,
                persisted.id(),
                processedEventCount,
                persisted.interestProfile().preferredTopics().size(),
                persisted.interestProfile().ignoredTopics().size(),
                durationMs
            );

            return new RecomputeCommandResult(
                toView(persisted),
                traceIdText,
                processedEventCount
            );
        } catch (RuntimeException exception) {
            String errorCode = resolveErrorCode(exception);
            String errorMessage = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            try {
                userPreferenceProfileTraceService.markRecomputeFailed(traceId, userId, processedEventCount, errorCode, errorMessage);
            } catch (RuntimeException traceException) {
                exception.addSuppressed(traceException);
            }
            log.error(
                "module=UserPreferenceProfileRecomputeService action=user_preference_profile_recompute_failed trace_id={} user_id={} processed_event_count={} result=FAILED duration_ms={} error_code={} error_message={}",
                traceIdText,
                userId,
                processedEventCount,
                durationMs(startedAt),
                errorCode,
                errorMessage
            );
            throw exception;
        }
    }

    private InterestProfile aggregateInterestProfile(List<UserActionEventRepository.UserActionEventRecord> recentEvents) {
        Map<String, Integer> preferredTopicCounts = new LinkedHashMap<>();
        Map<String, Integer> ignoredTopicCounts = new LinkedHashMap<>();
        Map<String, SourceFeedbackCounter> sourceFeedbackBySource = new LinkedHashMap<>();
        int saveAsNoteCount = 0;
        int promoteToIdeaCount = 0;
        int ignoreTrendCount = 0;
        int reviewPositiveCount = 0;
        int reviewNegativeCount = 0;
        int ideaFollowUpPositiveCount = 0;
        int ideaFollowUpNegativeCount = 0;
        boolean hasSupportedSignal = false;

        for (UserActionEventRepository.UserActionEventRecord event : recentEvents) {
            String eventType = event.eventType();
            Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
            switch (eventType) {
                case "TREND_SAVED_AS_NOTE" -> {
                    hasSupportedSignal = true;
                    saveAsNoteCount++;
                    collectTopics(preferredTopicCounts, extractTopics(payload));
                    markSourceFeedback(sourceFeedbackBySource, payload, true);
                }
                case "TREND_PROMOTED_TO_IDEA" -> {
                    hasSupportedSignal = true;
                    promoteToIdeaCount++;
                    ideaFollowUpPositiveCount++;
                    collectTopics(preferredTopicCounts, extractTopics(payload));
                    markSourceFeedback(sourceFeedbackBySource, payload, true);
                }
                case "TREND_ITEM_IGNORED" -> {
                    hasSupportedSignal = true;
                    ignoreTrendCount++;
                    collectTopics(ignoredTopicCounts, extractTopics(payload));
                    markSourceFeedback(sourceFeedbackBySource, payload, false);
                }
                case "REVIEW_COMPLETED", "REVIEW_PARTIAL" -> {
                    hasSupportedSignal = true;
                    reviewPositiveCount++;
                }
                case "REVIEW_NOT_STARTED", "REVIEW_ABANDONED" -> {
                    hasSupportedSignal = true;
                    reviewNegativeCount++;
                }
                case "TASK_COMPLETED" -> {
                    String taskType = readString(payload, "task_type");
                    if ("REVIEW_FOLLOW_UP".equals(taskType)) {
                        hasSupportedSignal = true;
                        reviewPositiveCount++;
                    }
                    if ("IDEA_NEXT_ACTION".equals(taskType)) {
                        hasSupportedSignal = true;
                        ideaFollowUpPositiveCount++;
                    }
                }
                case "TASK_SKIPPED" -> {
                    String taskType = readString(payload, "task_type");
                    if ("REVIEW_FOLLOW_UP".equals(taskType)) {
                        hasSupportedSignal = true;
                        reviewNegativeCount++;
                    }
                    if ("IDEA_NEXT_ACTION".equals(taskType)) {
                        hasSupportedSignal = true;
                        ideaFollowUpNegativeCount++;
                    }
                }
                case "SYSTEM_TASK_COMPLETED_FROM_REVIEW" -> {
                    hasSupportedSignal = true;
                    reviewPositiveCount++;
                }
                default -> {
                }
            }
        }

        if (!hasSupportedSignal) {
            return InterestProfile.empty();
        }

        return new InterestProfile(
            topTopics(preferredTopicCounts, TOPIC_LIMIT),
            topTopics(ignoredTopicCounts, TOPIC_LIMIT),
            sourceWeights(sourceFeedbackBySource),
            actionBias(saveAsNoteCount, promoteToIdeaCount, ignoreTrendCount),
            taskBias(reviewPositiveCount, reviewNegativeCount, ideaFollowUpPositiveCount, ideaFollowUpNegativeCount)
        );
    }

    private void collectTopics(Map<String, Integer> counter, List<String> topics) {
        for (String topic : topics) {
            counter.merge(topic, 1, Integer::sum);
        }
    }

    private List<String> extractTopics(Map<String, Object> payload) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        collectTopicValue(payload.get("topic_tags"), topics);
        collectTopicValue(payload.get("analysis_topic_tags"), topics);
        return List.copyOf(topics);
    }

    private void collectTopicValue(Object rawValue, LinkedHashSet<String> collector) {
        if (!(rawValue instanceof List<?> list)) {
            return;
        }
        for (Object value : list) {
            String topic = normalizeTopic(value);
            if (topic != null) {
                collector.add(topic);
            }
        }
    }

    private String normalizeTopic(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private void markSourceFeedback(Map<String, SourceFeedbackCounter> sourceFeedbackBySource,
                                    Map<String, Object> payload,
                                    boolean positive) {
        String sourceType = readString(payload, "source_type");
        if (sourceType == null) {
            return;
        }
        SourceFeedbackCounter counter = sourceFeedbackBySource.computeIfAbsent(sourceType, ignored -> new SourceFeedbackCounter());
        if (positive) {
            counter.positiveCount++;
        } else {
            counter.negativeCount++;
        }
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> topTopics(Map<String, Integer> counter, int limit) {
        if (counter.isEmpty()) {
            return List.of();
        }
        return counter.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(Math.max(1, limit))
            .map(Map.Entry::getKey)
            .toList();
    }

    private Map<String, Double> sourceWeights(Map<String, SourceFeedbackCounter> sourceFeedbackBySource) {
        if (sourceFeedbackBySource.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        sourceFeedbackBySource.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                SourceFeedbackCounter counter = entry.getValue();
                int total = counter.positiveCount + counter.negativeCount;
                if (total > 0) {
                    weights.put(entry.getKey(), ratio(counter.positiveCount, total));
                }
            });
        return weights.isEmpty() ? Map.of() : Map.copyOf(weights);
    }

    private Map<String, Double> actionBias(int saveAsNoteCount, int promoteToIdeaCount, int ignoreTrendCount) {
        int total = saveAsNoteCount + promoteToIdeaCount + ignoreTrendCount;
        if (total == 0) {
            return Map.of();
        }
        Map<String, Double> bias = new LinkedHashMap<>();
        bias.put("save_as_note", ratio(saveAsNoteCount, total));
        bias.put("promote_to_idea", ratio(promoteToIdeaCount, total));
        bias.put("ignore_trend", ratio(ignoreTrendCount, total));
        return Map.copyOf(bias);
    }

    private Map<String, Double> taskBias(int reviewPositiveCount,
                                         int reviewNegativeCount,
                                         int ideaFollowUpPositiveCount,
                                         int ideaFollowUpNegativeCount) {
        int reviewSignalTotal = reviewPositiveCount + reviewNegativeCount;
        int ideaSignalTotal = ideaFollowUpPositiveCount + ideaFollowUpNegativeCount;
        if (reviewSignalTotal + ideaSignalTotal == 0) {
            return Map.of();
        }
        Map<String, Double> bias = new LinkedHashMap<>();
        if (reviewSignalTotal > 0) {
            bias.put("review", ratio(reviewPositiveCount, reviewSignalTotal));
        }
        if (ideaSignalTotal > 0) {
            bias.put("idea_followup", ratio(ideaFollowUpPositiveCount, ideaSignalTotal));
        }
        return Map.copyOf(bias);
    }

    private double ratio(int value, int total) {
        return total <= 0 ? 0d : (double) value / (double) total;
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String resolveErrorCode(RuntimeException exception) {
        if (exception instanceof ApiException apiException && apiException.errorCode() != null && !apiException.errorCode().isBlank()) {
            return apiException.errorCode();
        }
        return DEFAULT_RECOMPUTE_ERROR_CODE;
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private UserPreferenceProfileApplicationService.UserPreferenceProfileView toView(
        UserPreferenceProfileRepository.UserPreferenceProfileRecord record
    ) {
        return new UserPreferenceProfileApplicationService.UserPreferenceProfileView(
            record.id(),
            record.userId(),
            record.interestProfile(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    public record RecomputeCommand(
        String userId
    ) {
    }

    public record RecomputeCommandResult(
        UserPreferenceProfileApplicationService.UserPreferenceProfileView profile,
        String traceId,
        int processedEventCount
    ) {
    }

    private static final class SourceFeedbackCounter {
        private int positiveCount;
        private int negativeCount;
    }
}
