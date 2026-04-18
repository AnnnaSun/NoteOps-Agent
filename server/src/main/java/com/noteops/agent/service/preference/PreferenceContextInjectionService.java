package com.noteops.agent.service.preference;

import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PreferenceContextInjectionService {

    private static final PreferenceContext EMPTY_CONTEXT = new PreferenceContext(false, Set.of(), Set.of(), Map.of(), Map.of());
    private static final PreferenceContextInjectionService NO_OP_INSTANCE = new PreferenceContextInjectionService(new NoOpUserPreferenceProfileRepository());

    private final UserPreferenceProfileRepository userPreferenceProfileRepository;

    public PreferenceContextInjectionService(UserPreferenceProfileRepository userPreferenceProfileRepository) {
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
    }

    public static PreferenceContextInjectionService noOp() {
        return NO_OP_INSTANCE;
    }

    public static PreferenceContext emptyContext() {
        return EMPTY_CONTEXT;
    }

    public PreferenceContext loadContext(UUID userId) {
        return userPreferenceProfileRepository.findByUserId(userId)
            .map(UserPreferenceProfileRepository.UserPreferenceProfileRecord::interestProfile)
            .map(this::toContext)
            .orElse(EMPTY_CONTEXT);
    }

    public TrendInjectionResult injectTrend(PreferenceContext context, TrendCandidate candidate) {
        if (context == null || !context.profileLoaded()) {
            return new TrendInjectionResult(0d, candidate.suggestedAction(), false, 0, 0);
        }

        TopicMatch topicMatch = matchTopics(context, candidate.topicTags());
        double sourceWeight = context.sourceWeights().getOrDefault(normalizeSourceType(candidate.sourceType()), 0d);
        double rankingDelta = sourceWeight * 20d + topicMatch.preferredMatches() * 4d - topicMatch.ignoredMatches() * 6d;

        String effectiveSuggestedAction = resolveTrendSuggestedAction(context, candidate, topicMatch);
        boolean overridden = effectiveSuggestedAction != null && !effectiveSuggestedAction.equals(candidate.suggestedAction());
        return new TrendInjectionResult(
            rankingDelta,
            effectiveSuggestedAction,
            overridden,
            topicMatch.preferredMatches(),
            topicMatch.ignoredMatches()
        );
    }

    public int scoreSearchRelatedByTopics(PreferenceContext context, List<String> candidateTopics) {
        if (context == null || !context.profileLoaded()) {
            return 0;
        }
        TopicMatch topicMatch = matchTopics(context, candidateTopics);
        double score = topicMatch.preferredMatches() * 8d - topicMatch.ignoredMatches() * 10d;
        return (int) Math.round(score);
    }

    private PreferenceContext toContext(InterestProfile profile) {
        return new PreferenceContext(
            true,
            normalizeTopics(profile.preferredTopics()),
            normalizeTopics(profile.ignoredTopics()),
            normalizeSourceWeights(profile.sourceWeights()),
            normalizeActionBias(profile.actionBias())
        );
    }

    private Set<String> normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String topic : topics) {
            String value = normalizeTopic(topic);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private Map<String, Double> normalizeSourceWeights(Map<String, Double> sourceWeights) {
        if (sourceWeights == null || sourceWeights.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        sourceWeights.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalizedKey = normalizeSourceType(key);
            if (normalizedKey != null) {
                normalized.put(normalizedKey, value);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private Map<String, Double> normalizeActionBias(Map<String, Double> actionBias) {
        if (actionBias == null || actionBias.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        actionBias.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalizedKey = normalizeActionKey(key);
            if (normalizedKey != null) {
                normalized.put(normalizedKey, value);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private TopicMatch matchTopics(PreferenceContext context, List<String> rawTopics) {
        if (rawTopics == null || rawTopics.isEmpty()) {
            return new TopicMatch(0, 0);
        }
        int preferred = 0;
        int ignored = 0;
        for (String rawTopic : rawTopics) {
            String topic = normalizeTopic(rawTopic);
            if (topic == null) {
                continue;
            }
            if (context.preferredTopics().contains(topic)) {
                preferred++;
            }
            if (context.ignoredTopics().contains(topic)) {
                ignored++;
            }
        }
        return new TopicMatch(preferred, ignored);
    }

    private String resolveTrendSuggestedAction(PreferenceContext context, TrendCandidate candidate, TopicMatch topicMatch) {
        String suggestedAction = candidate.suggestedAction();
        double ignoreBias = context.actionBias().getOrDefault("ignore_trend", 0d);
        double saveBias = context.actionBias().getOrDefault("save_as_note", 0d);
        double promoteBias = context.actionBias().getOrDefault("promote_to_idea", 0d);

        if (topicMatch.ignoredMatches() > 0 && ignoreBias >= 0.5d && ignoreBias >= saveBias && ignoreBias >= promoteBias) {
            return "IGNORE";
        }
        if (candidate.ideaWorthy() && promoteBias > saveBias && promoteBias >= 0.5d) {
            return "PROMOTE_TO_IDEA";
        }
        if (candidate.noteWorthy() && saveBias >= promoteBias && saveBias >= 0.5d) {
            return "SAVE_AS_NOTE";
        }
        return suggestedAction;
    }

    private String normalizeTopic(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeActionKey(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSourceType(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private record TopicMatch(
        int preferredMatches,
        int ignoredMatches
    ) {
    }

    public record PreferenceContext(
        boolean profileLoaded,
        Set<String> preferredTopics,
        Set<String> ignoredTopics,
        Map<String, Double> sourceWeights,
        Map<String, Double> actionBias
    ) {
    }

    public record TrendCandidate(
        String sourceType,
        List<String> topicTags,
        boolean noteWorthy,
        boolean ideaWorthy,
        String suggestedAction
    ) {
    }

    public record TrendInjectionResult(
        double rankingDelta,
        String effectiveSuggestedAction,
        boolean overridden,
        int preferredTopicMatches,
        int ignoredTopicMatches
    ) {
    }

    private static final class NoOpUserPreferenceProfileRepository implements UserPreferenceProfileRepository {
        @Override
        public Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
            return Optional.empty();
        }

        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            Instant now = Instant.now();
            return new UserPreferenceProfileRecord(UUID.randomUUID(), userId, interestProfile, now, now);
        }
    }
}
