package com.noteops.agent.controller.sync;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.sync.SyncActionsApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@Import(ApiExceptionHandler.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SyncActionsApplicationService syncActionsApplicationService;

    @Test
    void returnsSyncResultEnvelope() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(syncActionsApplicationService.apply(any())).thenReturn(
            new SyncActionsApplicationService.SyncActionsResult(
                List.of(
                    new SyncActionsApplicationService.AcceptedActionView(
                        "offline-1",
                        "REVIEW_COMPLETE",
                        "REVIEW_STATE",
                        reviewId,
                        false
                    )
                ),
                List.of(
                    new SyncActionsApplicationService.RejectedActionView(
                        "offline-2",
                        "REVIEW_COMPLETE",
                        "REVIEW_STATE",
                        reviewId,
                        "REVIEW_ITEM_NOT_FOUND",
                        "review item not found",
                        false,
                        false
                    )
                ),
                "trace-123",
                Instant.parse("2026-04-16T09:00:00Z")
            )
        );

        mockMvc.perform(post("/api/v1/sync/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "client_id": "web-client-a",
                      "actions": [
                        {
                          "offline_action_id": "offline-1",
                          "action_type": "REVIEW_COMPLETE",
                          "entity_type": "REVIEW_STATE",
                          "entity_id": "%s",
                          "payload": {
                            "completion_status": "COMPLETED",
                            "self_recall_result": "GOOD"
                          },
                          "occurred_at": "2026-04-16T08:58:00Z"
                        }
                      ]
                    }
                    """.formatted(reviewId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-123"))
            .andExpect(jsonPath("$.data.accepted[0].offline_action_id").value("offline-1"))
            .andExpect(jsonPath("$.data.accepted[0].entity_id").value(reviewId.toString()))
            .andExpect(jsonPath("$.data.rejected[0].offline_action_id").value("offline-2"))
            .andExpect(jsonPath("$.data.rejected[0].error_code").value("REVIEW_ITEM_NOT_FOUND"))
            .andExpect(jsonPath("$.data.rejected[0].retryable").value(false))
            .andExpect(jsonPath("$.data.server_sync_cursor").value("2026-04-16T09:00:00Z"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsBadRequestWhenUserIdIsInvalid() throws Exception {
        when(syncActionsApplicationService.apply(any()))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "user_id must be a valid UUID"));

        mockMvc.perform(post("/api/v1/sync/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "bad-user-id",
                      "client_id": "web-client-a",
                      "actions": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_USER_ID"));
    }
}
