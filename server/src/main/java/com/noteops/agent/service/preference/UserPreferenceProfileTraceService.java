package com.noteops.agent.service.preference;

import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPreferenceProfileTraceService {

    private static final String UPSERT_TRACE_ENTRY_TYPE = "USER_PREFERENCE_PROFILE_UPSERT";
    private static final String RECOMPUTE_TRACE_ENTRY_TYPE = "USER_PREFERENCE_PROFILE_RECOMPUTE";

    private final AgentTraceRepository agentTraceRepository;

    public UserPreferenceProfileTraceService(AgentTraceRepository agentTraceRepository) {
        this.agentTraceRepository = agentTraceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createUpsertTrace(UUID userId) {
        return agentTraceRepository.create(
            userId,
            UPSERT_TRACE_ENTRY_TYPE,
            "Persist user interest profile baseline",
            "USER",
            userId,
            List.of("preference-profile-service"),
            Map.of("user_id", userId, "profile_type", "INTEREST_PROFILE", "action", "UPSERT")
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createRecomputeTrace(UUID userId, int windowSize) {
        return agentTraceRepository.create(
            userId,
            RECOMPUTE_TRACE_ENTRY_TYPE,
            "Recompute user interest profile from action events",
            "USER",
            userId,
            List.of("preference-recompute-service"),
            Map.of(
                "user_id", userId,
                "profile_type", "INTEREST_PROFILE",
                "action", "RECOMPUTE",
                "event_window_size", windowSize
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUpsertCompleted(UUID traceId, UUID userId, UUID profileId, InterestProfile interestProfile) {
        agentTraceRepository.markCompleted(
            traceId,
            "Persisted user preference profile " + profileId,
            Map.of(
                "user_id", userId,
                "profile_id", profileId,
                "profile_type", "INTEREST_PROFILE",
                "preferred_topics_count", interestProfile.preferredTopics().size(),
                "ignored_topics_count", interestProfile.ignoredTopics().size(),
                "result", "UPSERTED"
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUpsertFailed(UUID traceId, UUID userId, String errorMessage) {
        agentTraceRepository.markFailed(
            traceId,
            "Failed to persist user preference profile",
            Map.of(
                "user_id", userId,
                "profile_type", "INTEREST_PROFILE",
                "result", "FAILED",
                "error_message", errorMessage
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRecomputeCompleted(UUID traceId,
                                       UUID userId,
                                       UUID profileId,
                                       InterestProfile interestProfile,
                                       int processedEventCount) {
        agentTraceRepository.markCompleted(
            traceId,
            "Recomputed user preference profile " + profileId,
            Map.of(
                "user_id", userId,
                "profile_id", profileId,
                "profile_type", "INTEREST_PROFILE",
                "preferred_topics_count", interestProfile.preferredTopics().size(),
                "ignored_topics_count", interestProfile.ignoredTopics().size(),
                "processed_event_count", processedEventCount,
                "result", "RECOMPUTED"
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRecomputeFailed(UUID traceId,
                                    UUID userId,
                                    int processedEventCount,
                                    String errorCode,
                                    String errorMessage) {
        agentTraceRepository.markFailed(
            traceId,
            "Failed to recompute user preference profile",
            Map.of(
                "user_id", userId,
                "profile_type", "INTEREST_PROFILE",
                "processed_event_count", processedEventCount,
                "result", "FAILED",
                "error_code", errorCode,
                "error_message", errorMessage
            )
        );
    }
}
