package com.noteops.agent.service.review;

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
public class DefaultReviewAiAssistant implements ReviewAiAssistant {

    private static final String RENDER_ROUTE_KEY = "review-render";
    private static final String FEEDBACK_ROUTE_KEY = "review-feedback";
    private static final String RENDER_REQUEST_TYPE = "REVIEW_RENDER";
    private static final String FEEDBACK_REQUEST_TYPE = "REVIEW_FEEDBACK";
    private static final String RENDER_TOOL_NAME = "review.ai-render";
    private static final String FEEDBACK_TOOL_NAME = "review.ai-feedback";

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final JsonSupport jsonSupport;

    public DefaultReviewAiAssistant(AiClient aiClient, ObjectMapper objectMapper, JsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.jsonSupport = jsonSupport;
    }

    @Override
    // 批量生成 today review 的 recall-friendly 视图，只返回展示建议，不参与业务裁决。
    public ReviewRenderResult renderTodayItems(ReviewRenderRequest request) {
        if (request.items().isEmpty()) {
            return new ReviewRenderResult(Map.of());
        }

        AiResponse response = aiClient.analyze(new AiRequest(
            request.userId(),
            request.traceId(),
            RENDER_ROUTE_KEY,
            RENDER_REQUEST_TYPE,
            RENDER_TOOL_NAME,
            renderSystemPrompt(),
            renderUserPrompt(request),
            AiResponseMode.JSON_OBJECT,
            renderResponseSchema(),
            Map.of("review_count", request.items().size()),
            null
        ));

        try {
            ReviewRenderAiResponse parsed = objectMapper.readValue(response.rawText(), ReviewRenderAiResponse.class);
            Map<UUID, RenderedReviewItem> itemsByReviewId = parsed.items() == null
                ? Map.of()
                : parsed.items().stream()
                    .filter(item -> item.reviewItemId() != null)
                    .collect(Collectors.toMap(
                        ReviewRenderAiItem::reviewItemId,
                        item -> new RenderedReviewItem(
                            item.reviewItemId(),
                            item.recallSummary(),
                            item.reviewKeyPoints(),
                            item.extensionPreview()
                        ),
                        (left, right) -> right,
                        LinkedHashMap::new
                    ));
            return new ReviewRenderResult(itemsByReviewId);
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "REVIEW_AI_RENDER_OUTPUT_INVALID",
                "review render ai output is invalid: " + exception.getMessage()
            );
        }
    }

    @Override
    // 基于 review 完成结果生成反馈摘要、下次提示和延伸建议，不直接改任务或状态。
    public ReviewFeedbackResult buildCompletionFeedback(ReviewFeedbackRequest request) {
        AiResponse response = aiClient.analyze(new AiRequest(
            request.userId(),
            request.traceId(),
            FEEDBACK_ROUTE_KEY,
            FEEDBACK_REQUEST_TYPE,
            FEEDBACK_TOOL_NAME,
            feedbackSystemPrompt(),
            feedbackUserPrompt(request),
            AiResponseMode.JSON_OBJECT,
            feedbackResponseSchema(),
            feedbackInputMetadata(request),
            null
        ));

        try {
            ReviewFeedbackAiResponse parsed = objectMapper.readValue(response.rawText(), ReviewFeedbackAiResponse.class);
            return new ReviewFeedbackResult(
                parsed.recallFeedbackSummary(),
                parsed.nextReviewHint(),
                parsed.extensionSuggestions(),
                parsed.followUpTaskSuggestion()
            );
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "REVIEW_AI_FEEDBACK_OUTPUT_INVALID",
                "review feedback ai output is invalid: " + exception.getMessage()
            );
        }
    }

    private String renderSystemPrompt() {
        return """
            你是 NoteOps Review 的最小 AI 展示层助手。
            你的任务是把当前解释层信息压缩成更适合 recall 的短视图，而不是写成长摘要。

            严格规则：
            - 只能基于 title、current_summary、current_key_points、current_tags 输出
            - recall_summary 必须短、直接、面向回忆触发
            - review_key_points 返回 2 到 4 条短句，优先保留真正需要回忆的点
            - extension_preview 只能是一句轻量提示，可为空字符串
            - 不要重写 raw content，不要生成长段落，不要扩展为 Idea 或 Task
            - 不要改变 Review 排程、状态、优先级或 recall queue 策略
            """;
    }

    private String renderUserPrompt(ReviewRenderRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", request.items());
        return """
            请基于下面 JSON 输入，为每个 review item 返回严格 JSON。

            输入：
            %s
            """.formatted(jsonSupport.write(payload));
    }

    private Map<String, Object> renderResponseSchema() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("properties", Map.of(
            "review_item_id", Map.of("type", "string"),
            "recall_summary", Map.of("type", "string"),
            "review_key_points", Map.of("type", "array", "items", Map.of("type", "string")),
            "extension_preview", Map.of("type", "string")
        ));
        itemSchema.put("required", List.of("review_item_id", "recall_summary", "review_key_points", "extension_preview"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
            "items", Map.of("type", "array", "items", itemSchema)
        ));
        schema.put("required", List.of("items"));
        return schema;
    }

    private String feedbackSystemPrompt() {
        return """
            你是 NoteOps Review 的最小 AI 反馈助手。
            你的输出只用于 review 完成后的解释与建议，不参与任何状态迁移或任务裁决。

            状态差异要求：
            - COMPLETED：给简短巩固总结和轻量下一次复习提示
            - PARTIAL：指出关键缺口并给补漏建议
            - NOT_STARTED：给低门槛重新开始建议，不要输出重动作
            - ABANDONED：结合 completion_reason 给保守建议，避免强推进

            严格规则：
            - recall_feedback_summary 保持简短
            - next_review_hint 只给一个清晰动作
            - extension_suggestions 返回 0 到 3 条简短建议
            - follow_up_task_suggestion 只是建议文本，可为空字符串
            - 不要输出任何状态变更命令，不要直接完成 task，不要改写原始内容
            """;
    }

    private String feedbackUserPrompt(ReviewFeedbackRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("review", request);
        return """
            请基于下面 JSON 输入返回严格 JSON。

            输入：
            %s
            """.formatted(jsonSupport.write(payload));
    }

    private Map<String, Object> feedbackInputMetadata(ReviewFeedbackRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("review_item_id", request.reviewItemId());
        metadata.put("note_id", request.noteId());
        metadata.put("queue_type", request.queueType().name());
        metadata.put("completion_status", request.completionStatus().name());
        if (request.completionReason() != null) {
            metadata.put("completion_reason", request.completionReason().name());
        }
        if (request.selfRecallResult() != null) {
            metadata.put("self_recall_result", request.selfRecallResult().name());
        }
        return metadata;
    }

    private Map<String, Object> feedbackResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
            "recall_feedback_summary", Map.of("type", "string"),
            "next_review_hint", Map.of("type", "string"),
            "extension_suggestions", Map.of("type", "array", "items", Map.of("type", "string")),
            "follow_up_task_suggestion", Map.of("type", "string")
        ));
        schema.put("required", List.of(
            "recall_feedback_summary",
            "next_review_hint",
            "extension_suggestions",
            "follow_up_task_suggestion"
        ));
        return schema;
    }

    private record ReviewRenderAiResponse(
        List<ReviewRenderAiItem> items
    ) {
    }

    private record ReviewRenderAiItem(
        @JsonProperty("review_item_id")
        UUID reviewItemId,
        @JsonProperty("recall_summary")
        String recallSummary,
        @JsonProperty("review_key_points")
        List<String> reviewKeyPoints,
        @JsonProperty("extension_preview")
        String extensionPreview
    ) {
    }

    private record ReviewFeedbackAiResponse(
        @JsonProperty("recall_feedback_summary")
        String recallFeedbackSummary,
        @JsonProperty("next_review_hint")
        String nextReviewHint,
        @JsonProperty("extension_suggestions")
        List<String> extensionSuggestions,
        @JsonProperty("follow_up_task_suggestion")
        String followUpTaskSuggestion
    ) {
    }
}
