package com.noteops.agent.controller.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.service.trend.TrendPlanApplicationService;
import com.noteops.agent.service.trend.TrendSourceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrendPlanController.class)
@Import(ApiExceptionHandler.class)
class TrendPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendPlanApplicationService trendPlanApplicationService;

    @Test
    void triggersIngestWithEnvelopeAndTopLevelTraceId() throws Exception {
        AtomicReference<String> traceIdRef = new AtomicReference<>();
        when(trendPlanApplicationService.triggerDefaultPlan(any())).thenAnswer(invocation -> {
            TrendPlanApplicationService.TriggerCommand command = invocation.getArgument(0);
            assertThat(command.traceId()).isNotBlank();
            UUID.fromString(command.traceId());
            traceIdRef.set(command.traceId());
            return new TrendPlanApplicationService.TriggerResult(
                "default_ai_engineering_trends",
                true,
                List.of(
                    new TrendSourceRegistry.ResolvedTrendSource(TrendSourceType.HN, "Hacker News"),
                    new TrendSourceRegistry.ResolvedTrendSource(TrendSourceType.GITHUB, "GitHub")
                ),
                5,
                "DAILY",
                List.of("agent", "llm", "memory", "retrieval", "tooling", "coding"),
                true,
                false,
                "INGEST",
                4,
                3,
                1,
                List.of(
                    new TrendPlanApplicationService.SourceResult("HN", "Hacker News", 2, 2, 0),
                    new TrendPlanApplicationService.SourceResult("GITHUB", "GitHub", 2, 1, 1)
                ),
                "COMPLETED",
                command.traceId()
            );
        });

        mockMvc.perform(post("/api/v1/trends/plans/default/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(traceIdRef.get()))
            .andExpect(jsonPath("$.data.plan_key").value("default_ai_engineering_trends"))
            .andExpect(jsonPath("$.data.trigger_mode").value("INGEST"))
            .andExpect(jsonPath("$.data.fetched_count").value(4))
            .andExpect(jsonPath("$.data.inserted_count").value(3))
            .andExpect(jsonPath("$.data.deduped_count").value(1))
            .andExpect(jsonPath("$.data.source_results[0].source_type").value("HN"))
            .andExpect(jsonPath("$.data.result").value("COMPLETED"));
    }

    @Test
    void returnsControlledErrorEnvelopeWhenIngestFails() throws Exception {
        AtomicReference<String> traceIdRef = new AtomicReference<>();
        when(trendPlanApplicationService.triggerDefaultPlan(any()))
            .thenAnswer(invocation -> {
                TrendPlanApplicationService.TriggerCommand command = invocation.getArgument(0);
                traceIdRef.set(command.traceId());
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "TREND_SOURCE_FETCH_FAILED",
                    "GitHub fetch failed",
                    command.traceId()
                );
            });

        mockMvc.perform(post("/api/v1/trends/plans/default/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111"
                    }
                    """))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(traceIdRef.get()))
            .andExpect(jsonPath("$.error.code").value("TREND_SOURCE_FETCH_FAILED"));
    }
}
