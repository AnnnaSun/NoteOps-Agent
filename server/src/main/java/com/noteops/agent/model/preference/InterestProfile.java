package com.noteops.agent.model.preference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record InterestProfile(
    List<String> preferredTopics,
    List<String> ignoredTopics,
    Map<String, Double> sourceWeights,
    Map<String, Double> actionBias,
    Map<String, Double> taskBias
) {

    public InterestProfile {
        preferredTopics = normalizeTopics(preferredTopics);
        ignoredTopics = normalizeTopics(ignoredTopics);
        sourceWeights = normalizeWeights(sourceWeights);
        actionBias = normalizeWeights(actionBias);
        taskBias = normalizeWeights(taskBias);
    }

    public static InterestProfile empty() {
        return new InterestProfile(List.of(), List.of(), Map.of(), Map.of(), Map.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preferred_topics", preferredTopics);
        payload.put("ignored_topics", ignoredTopics);
        payload.put("source_weights", sourceWeights);
        payload.put("action_bias", actionBias);
        payload.put("task_bias", taskBias);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static InterestProfile fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        return new InterestProfile(
            toStringList(raw.get("preferred_topics")),
            toStringList(raw.get("ignored_topics")),
            toWeightMap(raw.get("source_weights")),
            toWeightMap(raw.get("action_bias")),
            toWeightMap(raw.get("task_bias"))
        );
    }

    private static List<String> normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String topic : topics) {
            if (topic == null) {
                continue;
            }
            String value = topic.trim();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static Map<String, Double> normalizeWeights(Map<String, Double> rawWeights) {
        if (rawWeights == null || rawWeights.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        rawWeights.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalizedKey = key.trim();
            if (normalizedKey.isEmpty()) {
                return;
            }
            normalized.put(normalizedKey, value);
        });
        return Map.copyOf(normalized);
    }

    private static List<String> toStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : rawList) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private static Map<String, Double> toWeightMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            Double parsed = parseWeight(value);
            if (key != null && parsed != null) {
                values.put(String.valueOf(key), parsed);
            }
        });
        return values;
    }

    private static Double parseWeight(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
