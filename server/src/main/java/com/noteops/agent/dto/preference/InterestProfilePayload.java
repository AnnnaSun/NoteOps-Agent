package com.noteops.agent.dto.preference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.model.preference.InterestProfile;

import java.util.List;
import java.util.Map;

public record InterestProfilePayload(
    @JsonProperty("preferred_topics")
    List<String> preferredTopics,
    @JsonProperty("ignored_topics")
    List<String> ignoredTopics,
    @JsonProperty("source_weights")
    Map<String, Double> sourceWeights,
    @JsonProperty("action_bias")
    Map<String, Double> actionBias,
    @JsonProperty("task_bias")
    Map<String, Double> taskBias
) {

    public InterestProfile toModel() {
        return new InterestProfile(preferredTopics, ignoredTopics, sourceWeights, actionBias, taskBias);
    }

    public static InterestProfilePayload from(InterestProfile interestProfile) {
        InterestProfile normalized = interestProfile == null ? InterestProfile.empty() : interestProfile;
        return new InterestProfilePayload(
            normalized.preferredTopics(),
            normalized.ignoredTopics(),
            normalized.sourceWeights(),
            normalized.actionBias(),
            normalized.taskBias()
        );
    }
}
