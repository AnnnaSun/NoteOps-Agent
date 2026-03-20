package com.noteops.agent.controller.workspace;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.review.ReviewApplicationService;
import com.noteops.agent.service.task.TaskApplicationService;
import com.noteops.agent.service.workspace.WorkspaceApplicationService;
import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
@Import(ApiExceptionHandler.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceApplicationService workspaceApplicationService;

    @Test
    void returnsTodayWorkspaceEnvelope() throws Exception {
        when(workspaceApplicationService.today("11111111-1111-1111-1111-111111111111", "+08:00")).thenReturn(
            new WorkspaceApplicationService.WorkspaceTodayView(
                List.of(
                    new ReviewApplicationService.ReviewTodayItemView(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ReviewQueueType.RECALL,
                        ReviewCompletionStatus.PARTIAL,
                        ReviewCompletionReason.TIME_LIMIT,
                        BigDecimal.valueOf(30),
                        Instant.parse("2026-03-16T01:00:00Z"),
                        24,
                        1,
                        "Recall note",
                        "summary",
                        List.of("point")
                    )
                ),
                List.of(
                    new TaskApplicationService.TaskView(
                        UUID.randomUUID(),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        null,
                        TaskSource.USER,
                        "GENERAL",
                        "Task title",
                        null,
                        TaskStatus.TODO,
                        1,
                        Instant.parse("2026-03-16T06:00:00Z"),
                        TaskRelatedEntityType.NONE,
                        null,
                        Instant.parse("2026-03-16T01:00:00Z"),
                        Instant.parse("2026-03-16T01:00:00Z")
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/workspace/today")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("timezone_offset", "+08:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.today_reviews[0].queue_type").value("RECALL"))
            .andExpect(jsonPath("$.data.today_tasks[0].task_source").value("USER"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsUpcomingWorkspaceEnvelope() throws Exception {
        when(workspaceApplicationService.upcoming("11111111-1111-1111-1111-111111111111", "+08:00")).thenReturn(
            new WorkspaceApplicationService.WorkspaceUpcomingView(
                List.of(),
                List.of(
                    new TaskApplicationService.TaskView(
                        UUID.randomUUID(),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        null,
                        TaskSource.SYSTEM,
                        "REVIEW_FOLLOW_UP",
                        "Tomorrow task",
                        null,
                        TaskStatus.TODO,
                        90,
                        Instant.parse("2026-03-17T06:00:00Z"),
                        TaskRelatedEntityType.REVIEW,
                        UUID.randomUUID(),
                        Instant.parse("2026-03-16T01:00:00Z"),
                        Instant.parse("2026-03-16T01:00:00Z")
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/workspace/upcoming")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("timezone_offset", "+08:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.upcoming_tasks[0].task_source").value("SYSTEM"))
            .andExpect(jsonPath("$.data.upcoming_reviews").isArray())
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidWorkspaceTimezone() throws Exception {
        when(workspaceApplicationService.upcoming("11111111-1111-1111-1111-111111111111", "Asia/Shanghai"))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE_OFFSET", "timezone_offset must be a valid UTC offset"));

        mockMvc.perform(get("/api/v1/workspace/upcoming")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("timezone_offset", "Asia/Shanghai"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_TIMEZONE_OFFSET"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
