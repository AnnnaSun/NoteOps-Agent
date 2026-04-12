package com.noteops.agent.model.trend;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record TrendAnalysisPayload(
    String summary,
    String whyItMatters,
    List<String> topicTags,
    String signalType,
    boolean noteWorthy,
    boolean ideaWorthy,
    TrendActionType suggestedAction,
    String reasoningSummary
) {

    public static TrendAnalysisPayload empty() {
        return new TrendAnalysisPayload(null, null, List.of(), null, false, false, null, null);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("why_it_matters", whyItMatters);
        payload.put("topic_tags", topicTags == null ? List.of() : topicTags);
        payload.put("signal_type", signalType);
        payload.put("note_worthy", noteWorthy);
        payload.put("idea_worthy", ideaWorthy);
        payload.put("suggested_action", suggestedAction == null ? null : suggestedAction.name());
        payload.put("reasoning_summary", reasoningSummary);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static TrendAnalysisPayload fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Object tags = raw.get("topic_tags");
        List<String> topicTags = tags instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();
        String suggestedAction = asString(raw.get("suggested_action"));
        return new TrendAnalysisPayload(
            asString(raw.get("summary")),
            asString(raw.get("why_it_matters")),
            topicTags,
            asString(raw.get("signal_type")),
            asBoolean(raw.get("note_worthy")),
            asBoolean(raw.get("idea_worthy")),
            parseSuggestedAction(suggestedAction),
            asString(raw.get("reasoning_summary"))
        );
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static TrendActionType parseSuggestedAction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TrendActionType.valueOf(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }
}
