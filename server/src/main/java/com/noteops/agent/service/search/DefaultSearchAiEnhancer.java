package com.noteops.agent.service.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.service.ai.AiClient;
import com.noteops.agent.service.ai.AiRequest;
import com.noteops.agent.service.ai.AiResponse;
import com.noteops.agent.service.ai.AiResponseMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DefaultSearchAiEnhancer implements SearchAiEnhancer {

    private static final String ROUTE_KEY = "search-enhancement";
    private static final String REQUEST_TYPE = "SEARCH_AI_ENHANCEMENT";
    private static final String TOOL_NAME = "search.ai-enhancement";
    private static final List<String> ALLOWED_RELATION_LABELS = List.of("可能更新", "可能冲突", "背景补充", "延伸阅读");

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final JsonSupport jsonSupport;

    public DefaultSearchAiEnhancer(AiClient aiClient, ObjectMapper objectMapper, JsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.jsonSupport = jsonSupport;
    }

    @Override
    // Search AI 增强入口：只生成关系解释和外部补充结构化信息，不直接决定写库行为。
    public SearchAiEnhancementResult enhance(SearchAiEnhancementRequest request) {
        if (request.relatedCandidates().isEmpty() && request.externalCandidates().isEmpty()) {
            return new SearchAiEnhancementResult(Map.of(), Map.of());
        }

        AiResponse response = aiClient.analyze(new AiRequest(
            request.userId(),
            request.traceId(),
            ROUTE_KEY,
            REQUEST_TYPE,
            TOOL_NAME,
            systemPrompt(),
            userPrompt(request),
            AiResponseMode.JSON_OBJECT,
            responseSchema(),
            inputMetadata(request),
            null
        ));

        try {
            SearchAiResponse parsed = objectMapper.readValue(response.rawText(), SearchAiResponse.class);
            return new SearchAiEnhancementResult(
                parsed.relatedMatches() == null
                    ? Map.of()
                    : parsed.relatedMatches().stream()
                        .filter(item -> item.noteId() != null)
                        .collect(Collectors.toMap(
                            SearchAiRelatedItem::noteId,
                            item -> new RelatedEnhancement(item.relationReason()),
                            (left, right) -> right,
                            LinkedHashMap::new
                        )),
                parsed.externalSupplements() == null
                    ? Map.of()
                    : parsed.externalSupplements().stream()
                        .filter(item -> item.sourceUri() != null)
                        .collect(Collectors.toMap(
                            SearchAiExternalItem::sourceUri,
                            item -> new ExternalEnhancement(item.relationLabel(), item.keywords(), item.summarySnippet()),
                            (left, right) -> right,
                            LinkedHashMap::new
                        ))
            );
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SEARCH_AI_OUTPUT_INVALID", "search ai output is invalid: " + exception.getMessage());
        }
    }

    private String systemPrompt() {
        return """
            你是 NoteOps Search 的最小 AI 增强器。
            你只允许输出：
            1. related_matches 的 relation_reason
            2. external_supplements 的 relation_label、keywords、summary_snippet

            严格规则：
            - relation_reason 必须是短句，可解释，优先引用共享主题、共享标签、来源接近、工作流链路等维度
            - 不要输出“语义相关”“相关内容”“可能有关”这类空泛表述
            - relation_label 只能是：可能更新、可能冲突、背景补充、延伸阅读
            - keywords 返回 1 到 4 个短词
            - summary_snippet 保持简短
            - 不要建议直接更新 notes.current_summary / current_key_points / current_tags
            - 不要输出任何落库命令或覆盖建议
            """;
    }

    private String userPrompt(SearchAiEnhancementRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", request.query());
        payload.put("related_candidates", request.relatedCandidates());
        payload.put("external_candidates", request.externalCandidates());
        return """
            请基于下面 JSON 输入返回严格 JSON。

            输入：
            %s
            """.formatted(jsonSupport.write(payload));
    }

    private Map<String, Object> inputMetadata(SearchAiEnhancementRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("query", request.query());
        metadata.put("related_candidate_count", request.relatedCandidates().size());
        metadata.put("external_candidate_count", request.externalCandidates().size());
        return metadata;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> relatedItem = new LinkedHashMap<>();
        relatedItem.put("type", "object");
        relatedItem.put("additionalProperties", false);
        relatedItem.put("properties", Map.of(
            "note_id", Map.of("type", "string"),
            "relation_reason", Map.of("type", "string")
        ));
        relatedItem.put("required", List.of("note_id", "relation_reason"));

        Map<String, Object> externalItem = new LinkedHashMap<>();
        externalItem.put("type", "object");
        externalItem.put("additionalProperties", false);
        externalItem.put("properties", Map.of(
            "source_uri", Map.of("type", "string"),
            "relation_label", Map.of("type", "string", "enum", ALLOWED_RELATION_LABELS),
            "keywords", Map.of("type", "array", "items", Map.of("type", "string")),
            "summary_snippet", Map.of("type", "string")
        ));
        externalItem.put("required", List.of("source_uri", "relation_label", "keywords", "summary_snippet"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
            "related_matches", Map.of("type", "array", "items", relatedItem),
            "external_supplements", Map.of("type", "array", "items", externalItem)
        ));
        schema.put("required", List.of("related_matches", "external_supplements"));
        return schema;
    }

    private record SearchAiResponse(
        @JsonProperty("related_matches")
        List<SearchAiRelatedItem> relatedMatches,
        @JsonProperty("external_supplements")
        List<SearchAiExternalItem> externalSupplements
    ) {
    }

    private record SearchAiRelatedItem(
        @JsonProperty("note_id")
        UUID noteId,
        @JsonProperty("relation_reason")
        String relationReason
    ) {
    }

    private record SearchAiExternalItem(
        @JsonProperty("source_uri")
        String sourceUri,
        @JsonProperty("relation_label")
        String relationLabel,
        List<String> keywords,
        @JsonProperty("summary_snippet")
        String summarySnippet
    ) {
    }
}
