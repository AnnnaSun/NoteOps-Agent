package com.noteops.agent.controller.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.dto.trend.TrendInboxItemResponse;
import com.noteops.agent.service.trend.TrendInboxQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrendInboxController.class)
@Import(ApiExceptionHandler.class)
class TrendInboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendInboxQueryService trendInboxQueryService;

    @Test
    void returnsTraceIdOnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        AtomicReference<TrendInboxQueryService.InboxQueryCommand> commandRef = new AtomicReference<>();

        when(trendInboxQueryService.list(any())).thenAnswer(invocation -> {
            TrendInboxQueryService.InboxQueryCommand command = invocation.getArgument(0);
            commandRef.set(command);
            return List.of(
                new TrendInboxItemResponse(
                    trendItemId.toString(),
                    userId.toString(),
                    "HN",
                    "hn-123",
                    "Trend title",
                    "https://example.com/trend",
                    "Compact summary",
                    91.5,
                    "ANALYZED",
                    "SAVE_AS_NOTE",
                    Map.of(
                        "summary", "Compact summary",
                        "why_it_matters", "It points to a useful note candidate",
                        "topic_tags", List.of("agents", "tooling"),
                        "signal_type", "DISCUSSION",
                        "note_worthy", true,
                        "idea_worthy", false,
                        "suggested_action", "SAVE_AS_NOTE",
                        "reasoning_summary", "High signal and reusable evidence"
                    ),
                    Instant.parse("2026-04-10T01:00:00Z"),
                    Instant.parse("2026-04-10T02:00:00Z"),
                    Instant.parse("2026-04-10T03:00:00Z")
                )
            );
        });

        MvcResult result = mockMvc.perform(get("/api/v1/trends/inbox")
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id", notNullValue()))
            .andExpect(jsonPath("$.data[0].trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data[0].status").value("ANALYZED"))
            .andExpect(jsonPath("$.data[0].source_type").value("HN"))
            .andExpect(jsonPath("$.meta.server_time").exists())
            .andReturn();

        TrendInboxQueryService.InboxQueryCommand command = commandRef.get();
        org.assertj.core.api.Assertions.assertThat(command).isNotNull();
        org.assertj.core.api.Assertions.assertThat(command.status()).isEqualTo("ANALYZED");
        org.assertj.core.api.Assertions.assertThat(command.sourceType()).isNull();
        String responseTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id");
        org.assertj.core.api.Assertions.assertThat(responseTraceId).isEqualTo(command.traceId());
    }

    @Test
    void returnsTraceIdAndErrorCodeOnServiceFailure() throws Exception {
        UUID userId = UUID.randomUUID();
        AtomicReference<TrendInboxQueryService.InboxQueryCommand> commandRef = new AtomicReference<>();

        when(trendInboxQueryService.list(any())).thenAnswer(invocation -> {
            TrendInboxQueryService.InboxQueryCommand command = invocation.getArgument(0);
            commandRef.set(command);
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TREND_STATUS",
                "status must be a valid trend item status",
                command.traceId()
            );
        });

        MvcResult result = mockMvc.perform(get("/api/v1/trends/inbox")
                .param("user_id", userId.toString())
                .param("status", "bad-status"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id", notNullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_TREND_STATUS"))
            .andExpect(jsonPath("$.meta.server_time").exists())
            .andReturn();

        TrendInboxQueryService.InboxQueryCommand command = commandRef.get();
        org.assertj.core.api.Assertions.assertThat(command).isNotNull();
        String responseTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id");
        org.assertj.core.api.Assertions.assertThat(responseTraceId).isEqualTo(command.traceId());
    }
}
