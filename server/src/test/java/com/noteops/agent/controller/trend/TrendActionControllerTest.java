package com.noteops.agent.controller.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.dto.trend.TrendActionRequest;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trend.TrendItemRepository;
import com.noteops.agent.service.trend.TrendActionApplicationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrendActionController.class)
@Import(ApiExceptionHandler.class)
class TrendActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TrendActionApplicationService trendActionApplicationService;

    @Test
    void returnsTraceIdAndTrendActionPayloadForIgnore() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        AtomicReference<TrendActionApplicationService.ActionCommand> commandRef = new AtomicReference<>();

        when(trendActionApplicationService.act(any())).thenAnswer(invocation -> {
            TrendActionApplicationService.ActionCommand command = invocation.getArgument(0);
            commandRef.set(command);
            return new TrendActionApplicationService.ActionResult(
                command.traceId(),
                new TrendItemRepository.TrendItemRecord(
                    trendItemId,
                    userId,
                    TrendSourceType.HN,
                    "hn-123",
                    "Trend title",
                    "https://example.com/trend",
                    "Compact summary",
                    91.5,
                    TrendAnalysisPayload.fromMap(Map.of(
                        "summary", "Compact summary",
                        "why_it_matters", "It points to a useful note candidate",
                        "topic_tags", List.of("agents", "tooling"),
                        "signal_type", "DISCUSSION",
                        "note_worthy", true,
                        "idea_worthy", false,
                        "suggested_action", "IGNORE",
                        "reasoning_summary", "High signal and reusable evidence"
                    )),
                    Map.of(),
                    TrendItemStatus.IGNORED,
                    TrendActionType.IGNORE,
                    Instant.parse("2026-04-10T01:00:00Z"),
                    Instant.parse("2026-04-10T02:00:00Z"),
                    null,
                    null,
                    Instant.parse("2026-04-10T03:00:00Z"),
                    Instant.parse("2026-04-10T04:00:00Z")
                )
            );
        });

        MvcResult result = mockMvc.perform(post("/api/v1/trends/{trendItemId}/actions", trendItemId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TrendActionRequest(
                    userId.toString(),
                    "IGNORE",
                    "Not relevant"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data.action_result").value("IGNORED"))
            .andExpect(jsonPath("$.meta.server_time").exists())
            .andReturn();

        TrendActionApplicationService.ActionCommand command = commandRef.get();
        assertThat(command).isNotNull();
        assertThat(command.trendItemId()).isEqualTo(trendItemId.toString());
        assertThat(command.userId()).isEqualTo(userId.toString());
        assertThat(command.action()).isEqualTo("IGNORE");
        assertThat(command.operatorNote()).isEqualTo("Not relevant");
        assertThat(command.traceId()).isNotBlank();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id")).isEqualTo(command.traceId());
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.data.trace_id")).isEqualTo(command.traceId());
    }

    @Test
    void returnsConvertedNoteIdForSaveAsNote() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID convertedNoteId = UUID.randomUUID();
        AtomicReference<TrendActionApplicationService.ActionCommand> commandRef = new AtomicReference<>();

        when(trendActionApplicationService.act(any())).thenAnswer(invocation -> {
            TrendActionApplicationService.ActionCommand command = invocation.getArgument(0);
            commandRef.set(command);
            return new TrendActionApplicationService.ActionResult(
                command.traceId(),
                new TrendItemRepository.TrendItemRecord(
                    trendItemId,
                    userId,
                    TrendSourceType.HN,
                    "hn-456",
                    "Trend title",
                    "https://example.com/trend",
                    "Compact summary",
                    91.5,
                    TrendAnalysisPayload.fromMap(Map.of(
                        "summary", "Compact summary",
                        "why_it_matters", "It points to a useful note candidate",
                        "topic_tags", List.of("agents", "tooling"),
                        "signal_type", "DISCUSSION",
                        "note_worthy", true,
                        "idea_worthy", false,
                        "suggested_action", "SAVE_AS_NOTE",
                        "reasoning_summary", "High signal and reusable evidence"
                    )),
                    Map.of(),
                    TrendItemStatus.SAVED_AS_NOTE,
                    TrendActionType.SAVE_AS_NOTE,
                    Instant.parse("2026-04-10T01:00:00Z"),
                    Instant.parse("2026-04-10T02:00:00Z"),
                    convertedNoteId,
                    null,
                    Instant.parse("2026-04-10T03:00:00Z"),
                    Instant.parse("2026-04-10T04:00:00Z")
                )
            );
        });

        MvcResult result = mockMvc.perform(post("/api/v1/trends/{trendItemId}/actions", trendItemId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TrendActionRequest(
                    userId.toString(),
                    "SAVE_AS_NOTE",
                    "Capture as note"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data.action_result").value("SAVED_AS_NOTE"))
            .andExpect(jsonPath("$.data.converted_note_id").value(convertedNoteId.toString()))
            .andExpect(jsonPath("$.data.converted_idea_id").value(nullValue()))
            .andReturn();

        TrendActionApplicationService.ActionCommand command = commandRef.get();
        assertThat(command).isNotNull();
        assertThat(command.action()).isEqualTo("SAVE_AS_NOTE");
        assertThat(command.operatorNote()).isEqualTo("Capture as note");
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id")).isEqualTo(command.traceId());
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.data.trace_id")).isEqualTo(command.traceId());
    }

    @Test
    void returnsConvertedIdeaIdForPromoteToIdea() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        UUID convertedIdeaId = UUID.randomUUID();
        AtomicReference<TrendActionApplicationService.ActionCommand> commandRef = new AtomicReference<>();

        when(trendActionApplicationService.act(any())).thenAnswer(invocation -> {
            TrendActionApplicationService.ActionCommand command = invocation.getArgument(0);
            commandRef.set(command);
            return new TrendActionApplicationService.ActionResult(
                command.traceId(),
                new TrendItemRepository.TrendItemRecord(
                    trendItemId,
                    userId,
                    TrendSourceType.HN,
                    "hn-789",
                    "Trend title",
                    "https://example.com/trend",
                    "Compact summary",
                    91.5,
                    TrendAnalysisPayload.fromMap(Map.of(
                        "summary", "Compact summary",
                        "why_it_matters", "It points to an idea candidate",
                        "topic_tags", List.of("agents", "ideas"),
                        "signal_type", "DISCUSSION",
                        "note_worthy", false,
                        "idea_worthy", true,
                        "suggested_action", "PROMOTE_TO_IDEA",
                        "reasoning_summary", "High signal and reusable structure"
                    )),
                    Map.of(),
                    TrendItemStatus.PROMOTED_TO_IDEA,
                    TrendActionType.PROMOTE_TO_IDEA,
                    Instant.parse("2026-04-10T01:00:00Z"),
                    Instant.parse("2026-04-10T02:00:00Z"),
                    null,
                    convertedIdeaId,
                    Instant.parse("2026-04-10T03:00:00Z"),
                    Instant.parse("2026-04-10T04:00:00Z")
                )
            );
        });

        MvcResult result = mockMvc.perform(post("/api/v1/trends/{trendItemId}/actions", trendItemId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TrendActionRequest(
                    userId.toString(),
                    "PROMOTE_TO_IDEA",
                    "Promote to idea"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data.action_result").value("PROMOTED_TO_IDEA"))
            .andExpect(jsonPath("$.data.converted_note_id").value(nullValue()))
            .andExpect(jsonPath("$.data.converted_idea_id").value(convertedIdeaId.toString()))
            .andReturn();

        TrendActionApplicationService.ActionCommand command = commandRef.get();
        assertThat(command).isNotNull();
        assertThat(command.action()).isEqualTo("PROMOTE_TO_IDEA");
        assertThat(command.operatorNote()).isEqualTo("Promote to idea");
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id")).isEqualTo(command.traceId());
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.data.trace_id")).isEqualTo(command.traceId());
    }

    @Test
    void returnsControlledErrorForDeferredActions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID trendItemId = UUID.randomUUID();
        AtomicReference<TrendActionApplicationService.ActionCommand> commandRef = new AtomicReference<>();

        when(trendActionApplicationService.act(any())).thenAnswer(invocation -> {
            TrendActionApplicationService.ActionCommand command = invocation.getArgument(0);
            commandRef.set(command);
            throw new ApiException(
                HttpStatus.CONFLICT,
                "TREND_ACTION_DEFERRED",
                "SAVE_AS_NOTE is deferred to Step 4.6",
                command.traceId()
            );
        });

        MvcResult result = mockMvc.perform(post("/api/v1/trends/{trendItemId}/actions", trendItemId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "action": "SAVE_AS_NOTE",
                      "operator_note": "Please convert later"
                    }
                    """.formatted(userId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TREND_ACTION_DEFERRED"))
            .andReturn();

        assertThat(commandRef.get()).isNotNull();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.trace_id")).isEqualTo(commandRef.get().traceId());
    }
}
