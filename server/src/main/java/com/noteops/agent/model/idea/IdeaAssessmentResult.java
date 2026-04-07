package com.noteops.agent.model.idea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdeaAssessmentResult(
    @JsonProperty("problem_statement")
    String problemStatement,
    @JsonProperty("target_user")
    String targetUser,
    @JsonProperty("core_hypothesis")
    String coreHypothesis,
    @JsonProperty("mvp_validation_path")
    List<String> mvpValidationPath,
    @JsonProperty("next_actions")
    List<String> nextActions,
    List<String> risks,
    @JsonProperty("reasoning_summary")
    String reasoningSummary
) {

    public IdeaAssessmentResult {
        // 统一清洗 assessment 里的字符串与数组，避免空白值写入 JSON 合同。
        problemStatement = normalize(problemStatement);
        targetUser = normalize(targetUser);
        coreHypothesis = normalize(coreHypothesis);
        mvpValidationPath = normalizeList(mvpValidationPath);
        nextActions = normalizeList(nextActions);
        risks = normalizeList(risks);
        reasoningSummary = normalize(reasoningSummary);
    }

    public static IdeaAssessmentResult empty() {
        return new IdeaAssessmentResult(null, null, null, List.of(), List.of(), List.of(), null);
    }

    public static IdeaAssessmentResult fromMap(Map<String, Object> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return empty();
        }
        return new IdeaAssessmentResult(
            normalize(stringValue(rawValue.get("problem_statement"))),
            normalize(stringValue(rawValue.get("target_user"))),
            normalize(stringValue(rawValue.get("core_hypothesis"))),
            listValue(rawValue.get("mvp_validation_path")),
            listValue(rawValue.get("next_actions")),
            listValue(rawValue.get("risks")),
            normalize(stringValue(rawValue.get("reasoning_summary")))
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("problem_statement", problemStatement);
        value.put("target_user", targetUser);
        value.put("core_hypothesis", coreHypothesis);
        value.put("mvp_validation_path", mvpValidationPath);
        value.put("next_actions", nextActions);
        value.put("risks", risks);
        value.put("reasoning_summary", reasoningSummary);
        return value;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> listValue(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return normalizeList(rawList.stream().map(String::valueOf).toList());
    }

    private static List<String> normalizeList(List<String> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream()
            .map(IdeaAssessmentResult::normalize)
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
