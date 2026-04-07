package com.noteops.agent.controller.review;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.review.ReviewApplicationService;
import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.review.ReviewSelfRecallResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(ApiExceptionHandler.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewApplicationService reviewApplicationService;

    @Test
    void returnsTodayReviewsWithEnvelope() throws Exception {
        UUID reviewId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        when(reviewApplicationService.listToday("11111111-1111-1111-1111-111111111111")).thenReturn(List.of(
            new ReviewApplicationService.ReviewTodayItemView(
                reviewId,
                noteId,
                ReviewQueueType.RECALL,
                ReviewCompletionStatus.PARTIAL,
                ReviewCompletionReason.TIME_LIMIT,
                BigDecimal.valueOf(40),
                Instant.parse("2026-03-16T01:00:00Z"),
                24,
                1,
                "A note",
                "summary",
                List.of("point"),
                List.of("tag-1"),
                null,
                List.of(),
                null
            )
        ));

        mockMvc.perform(get("/api/v1/reviews/today").param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data[0].id").value(reviewId.toString()))
            .andExpect(jsonPath("$.data[0].queue_type").value("RECALL"))
            .andExpect(jsonPath("$.data[0].completion_reason").value("TIME_LIMIT"))
            .andExpect(jsonPath("$.data[0].ai_recall_summary").value(nullValue()))
            .andExpect(jsonPath("$.data[0].ai_review_key_points").isEmpty())
            .andExpect(jsonPath("$.data[0].ai_extension_preview").value(nullValue()))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsPrepWithEnvelope() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewApplicationService.getPrep(eq(reviewId.toString()), eq("11111111-1111-1111-1111-111111111111"))).thenReturn(
            new ReviewApplicationService.ReviewPrepView(
                reviewId,
                "prep summary",
                List.of("prep point 1", "prep point 2"),
                "prep extension"
            )
        );

        mockMvc.perform(get("/api/v1/reviews/{reviewItemId}/prep", reviewId)
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.review_item_id").value(reviewId.toString()))
            .andExpect(jsonPath("$.data.ai_recall_summary").value("prep summary"))
            .andExpect(jsonPath("$.data.ai_review_key_points[0]").value("prep point 1"))
            .andExpect(jsonPath("$.data.ai_extension_preview").value("prep extension"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsFeedbackWithEnvelope() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewApplicationService.getFeedback(eq(reviewId.toString()), eq("11111111-1111-1111-1111-111111111111"))).thenReturn(
            new ReviewApplicationService.ReviewFeedbackView(
                reviewId,
                "feedback summary",
                "next hint",
                List.of("suggestion-1", "suggestion-2"),
                "task suggestion"
            )
        );

        mockMvc.perform(get("/api/v1/reviews/{reviewItemId}/feedback", reviewId)
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.review_item_id").value(reviewId.toString()))
            .andExpect(jsonPath("$.data.recall_feedback_summary").value("feedback summary"))
            .andExpect(jsonPath("$.data.next_review_hint").value("next hint"))
            .andExpect(jsonPath("$.data.extension_suggestions[0]").value("suggestion-1"))
            .andExpect(jsonPath("$.data.follow_up_task_suggestion").value("task suggestion"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsBadRequestWhenTodayUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/today"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("MISSING_REQUEST_PARAMETER"));
    }

    @Test
    void completesReviewWithEnvelope() throws Exception {
        UUID reviewId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        when(reviewApplicationService.complete(eq(reviewId.toString()), any())).thenReturn(
            new ReviewApplicationService.ReviewCompletionView(
                reviewId,
                noteId,
                ReviewQueueType.RECALL,
                ReviewCompletionStatus.COMPLETED,
                null,
                ReviewSelfRecallResult.GOOD,
                "Strong recall",
                Instant.parse("2026-03-19T01:00:00Z"),
                0,
                0,
                BigDecimal.valueOf(20),
                null,
                null,
                List.of(),
                null
            )
        );

        mockMvc.perform(post("/api/v1/reviews/{reviewItemId}/complete", reviewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "completion_status": "COMPLETED",
                      "self_recall_result": "GOOD",
                      "note": "Strong recall"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.id").value(reviewId.toString()))
            .andExpect(jsonPath("$.data.queue_type").value("RECALL"))
            .andExpect(jsonPath("$.data.self_recall_result").value("GOOD"))
            .andExpect(jsonPath("$.data.note").value("Strong recall"))
            .andExpect(jsonPath("$.data.mastery_score").value(20))
            .andExpect(jsonPath("$.data.recall_feedback_summary").value(nullValue()))
            .andExpect(jsonPath("$.data.next_review_hint").value(nullValue()))
            .andExpect(jsonPath("$.data.extension_suggestions").isEmpty())
            .andExpect(jsonPath("$.data.follow_up_task_suggestion").value(nullValue()))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidPrepBody() throws Exception {
        when(reviewApplicationService.getPrep(eq("bad-id"), eq("11111111-1111-1111-1111-111111111111")))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID"));

        mockMvc.perform(get("/api/v1/reviews/{reviewItemId}/prep", "bad-id")
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_REVIEW_ITEM_ID"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidFeedbackBody() throws Exception {
        when(reviewApplicationService.getFeedback(eq("bad-id"), eq("11111111-1111-1111-1111-111111111111")))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID"));

        mockMvc.perform(get("/api/v1/reviews/{reviewItemId}/feedback", "bad-id")
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_REVIEW_ITEM_ID"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidRecallFeedbackCombination() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewApplicationService.complete(eq(reviewId.toString()), any()))
            .thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "RECALL_FEEDBACK_NOT_ALLOWED",
                "self_recall_result and note are only supported for RECALL queue"
            ));

        mockMvc.perform(post("/api/v1/reviews/{reviewItemId}/complete", reviewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "completion_status": "COMPLETED",
                      "self_recall_result": "GOOD"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RECALL_FEEDBACK_NOT_ALLOWED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidCompleteBody() throws Exception {
        when(reviewApplicationService.complete(eq("bad-id"), any()))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_ITEM_ID", "review_item_id must be a valid UUID"));

        mockMvc.perform(post("/api/v1/reviews/{reviewItemId}/complete", "bad-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "completion_status": "COMPLETED"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_REVIEW_ITEM_ID"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
