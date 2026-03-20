package com.noteops.agent.model.capture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureAnalysisResult(
    @JsonProperty("title_candidate")
    String titleCandidate,
    String summary,
    @JsonProperty("key_points")
    List<String> keyPoints,
    List<String> tags,
    @JsonProperty("idea_candidate")
    String ideaCandidate,
    Double confidence,
    String language,
    List<String> warnings
) {

    public CaptureAnalysisResult {
        // 统一把 LLM 返回的字符串和数组做 trim / 去空处理，避免脏值进入领域对象。
        titleCandidate = normalize(titleCandidate);
        summary = normalize(summary);
        keyPoints = normalizeList(keyPoints);
        tags = normalizeList(tags);
        ideaCandidate = normalize(ideaCandidate);
        language = normalize(language);
        warnings = normalizeList(warnings);
    }

    public static CaptureAnalysisResult fromMap(Map<String, Object> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }
        // 从 JSON/Map 恢复结果时，兼容数字、字符串和列表的不同存储形态。
        return new CaptureAnalysisResult(
            normalize(stringValue(rawValue.get("title_candidate"))),
            normalize(stringValue(rawValue.get("summary"))),
            listValue(rawValue.get("key_points")),
            listValue(rawValue.get("tags")),
            normalize(stringValue(rawValue.get("idea_candidate"))),
            doubleValue(rawValue.get("confidence")),
            normalize(stringValue(rawValue.get("language"))),
            listValue(rawValue.get("warnings"))
        );
    }

    public boolean isEmpty() {
        return titleCandidate == null
            && summary == null
            && keyPoints.isEmpty()
            && tags.isEmpty()
            && ideaCandidate == null
            && confidence == null
            && language == null
            && warnings.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        // 仅写入非空字段，减少持久化 JSON 噪音并保持字段语义清晰。
        if (titleCandidate != null) {
            value.put("title_candidate", titleCandidate);
        }
        if (summary != null) {
            value.put("summary", summary);
        }
        value.put("key_points", keyPoints);
        value.put("tags", tags);
        value.put("idea_candidate", ideaCandidate);
        value.put("confidence", confidence);
        value.put("language", language);
        value.put("warnings", warnings);
        return value;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> listValue(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return normalizeList(rawList.stream().map(Object::toString).toList());
    }

    private static List<String> normalizeList(List<String> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream()
            .map(CaptureAnalysisResult::normalize)
            .filter(item -> item != null)
            .toList();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
