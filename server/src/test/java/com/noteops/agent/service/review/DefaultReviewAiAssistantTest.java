package com.noteops.agent.service.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.review.ReviewSelfRecallResult;
import com.noteops.agent.service.ai.AiClient;
import com.noteops.agent.service.ai.AiProvider;
import com.noteops.agent.service.ai.AiRequest;
import com.noteops.agent.service.ai.AiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultReviewAiAssistantTest {

    @Test
    void buildsRenderAiRequestAndParsesStructuredResult() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RecordingAiClient aiClient = new RecordingAiClient("""
            {
              "items": [
                {
                  "review_item_id": "11111111-1111-1111-1111-111111111111",
                  "recall_summary": "短摘要",
                  "review_key_points": ["点一", "点二"],
                  "extension_preview": "延伸提示"
                }
              ]
            }
            """);
        DefaultReviewAiAssistant assistant = new DefaultReviewAiAssistant(aiClient, objectMapper, new JsonSupport(objectMapper));

        ReviewAiAssistant.ReviewRenderResult result = assistant.renderTodayItems(new ReviewAiAssistant.ReviewRenderRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(
                new ReviewAiAssistant.ReviewRenderCandidate(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.randomUUID(),
                    "Note",
                    "summary",
                    List.of("point"),
                    List.of("tag"),
                    ReviewQueueType.SCHEDULE,
                    Instant.parse("2026-03-16T01:00:00Z"),
                    0
                )
            )
        ));

        assertThat(aiClient.lastRequest.routeKey()).isEqualTo("review-render");
        assertThat(aiClient.lastRequest.requestType()).isEqualTo("REVIEW_RENDER");
        assertThat(aiClient.lastRequest.toolName()).isEqualTo("review.ai-render");
        assertThat(aiClient.lastRequest.responseSchema()).containsKey("properties");
        assertThat(result.itemsByReviewItemId()).containsKey(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(result.itemsByReviewItemId().get(UUID.fromString("11111111-1111-1111-1111-111111111111")).reviewKeyPoints())
            .containsExactly("点一", "点二");
    }

    @Test
    void buildsFeedbackAiRequestAndParsesStructuredResult() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RecordingAiClient aiClient = new RecordingAiClient("""
            {
              "recall_feedback_summary": "反馈摘要",
              "next_review_hint": "下一次先回忆第一条关键点",
              "extension_suggestions": ["建议一", "建议二"],
              "follow_up_task_suggestion": "可以创建后续任务"
            }
            """);
        DefaultReviewAiAssistant assistant = new DefaultReviewAiAssistant(aiClient, objectMapper, new JsonSupport(objectMapper));

        ReviewAiAssistant.ReviewFeedbackResult result = assistant.buildCompletionFeedback(new ReviewAiAssistant.ReviewFeedbackRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Note",
            "summary",
            List.of("point"),
            List.of("tag"),
            ReviewQueueType.RECALL,
            ReviewCompletionStatus.PARTIAL,
            ReviewCompletionReason.TIME_LIMIT,
            ReviewSelfRecallResult.VAGUE,
            "need more work"
        ));

        assertThat(aiClient.lastRequest.routeKey()).isEqualTo("review-feedback");
        assertThat(aiClient.lastRequest.requestType()).isEqualTo("REVIEW_FEEDBACK");
        assertThat(aiClient.lastRequest.toolName()).isEqualTo("review.ai-feedback");
        assertThat(result.recallFeedbackSummary()).isEqualTo("反馈摘要");
        assertThat(result.extensionSuggestions()).containsExactly("建议一", "建议二");
    }

    @Test
    void rejectsInvalidFeedbackOutput() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RecordingAiClient aiClient = new RecordingAiClient("{\"bad\":true}");
        DefaultReviewAiAssistant assistant = new DefaultReviewAiAssistant(aiClient, objectMapper, new JsonSupport(objectMapper));

        assertThatThrownBy(() -> assistant.buildCompletionFeedback(new ReviewAiAssistant.ReviewFeedbackRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Note",
            "summary",
            List.of("point"),
            List.of("tag"),
            ReviewQueueType.SCHEDULE,
            ReviewCompletionStatus.COMPLETED,
            null,
            null,
            null
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("review feedback ai output is invalid");
    }

    private static final class RecordingAiClient implements AiClient {

        private final String rawText;
        private AiRequest lastRequest;

        private RecordingAiClient(String rawText) {
            this.rawText = rawText;
        }

        @Override
        public AiResponse analyze(AiRequest request) {
            lastRequest = request;
            return new AiResponse(AiProvider.OPENAI_COMPATIBLE, "gateway-model", rawText, 12);
        }
    }
}
