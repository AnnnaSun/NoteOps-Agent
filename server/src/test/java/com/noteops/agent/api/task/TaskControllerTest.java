package com.noteops.agent.api.task;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.api.ApiExceptionHandler;
import com.noteops.agent.application.task.TaskApplicationService;
import com.noteops.agent.domain.task.TaskRelatedEntityType;
import com.noteops.agent.domain.task.TaskSource;
import com.noteops.agent.domain.task.TaskStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(ApiExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskApplicationService taskApplicationService;

    @Test
    void createsTaskWithEnvelope() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(taskApplicationService.create(any())).thenReturn(new TaskApplicationService.TaskCommandResult(
            new TaskApplicationService.TaskView(
                taskId,
                userId,
                null,
                TaskSource.USER,
                "GENERAL",
                "Write summary",
                null,
                TaskStatus.TODO,
                0,
                null,
                TaskRelatedEntityType.NONE,
                null,
                Instant.parse("2026-03-16T01:00:00Z"),
                Instant.parse("2026-03-16T01:00:00Z")
            ),
            "trace-1"
        ));

        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "title": "Write summary"
                    }
                    """.formatted(userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-1"))
            .andExpect(jsonPath("$.data.id").value(taskId.toString()))
            .andExpect(jsonPath("$.data.task_source").value("USER"))
            .andExpect(jsonPath("$.data.related_entity_type").value("NONE"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsConflictEnvelopeForDuplicateOpenUserTask() throws Exception {
        UUID userId = UUID.randomUUID();

        when(taskApplicationService.create(any()))
            .thenThrow(new ApiException(HttpStatus.CONFLICT, "OPEN_TASK_ALREADY_EXISTS", "an open user task with the same title and binding already exists"));

        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "title": "跟进这次 review",
                      "task_type": "REVIEW_ACTION"
                    }
                    """.formatted(userId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("OPEN_TASK_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsTodayTasksWithTaskSource() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(taskApplicationService.listToday(userId.toString(), "+08:00")).thenReturn(List.of(
            new TaskApplicationService.TaskView(
                taskId,
                userId,
                UUID.randomUUID(),
                TaskSource.SYSTEM,
                "REVIEW_FOLLOW_UP",
                "Follow up review",
                "Review needs follow-up.",
                TaskStatus.TODO,
                90,
                Instant.parse("2026-03-16T06:00:00Z"),
                TaskRelatedEntityType.REVIEW,
                UUID.randomUUID(),
                Instant.parse("2026-03-16T01:00:00Z"),
                Instant.parse("2026-03-16T01:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/v1/tasks/today")
                .param("user_id", userId.toString())
                .param("timezone_offset", "+08:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data[0].task_source").value("SYSTEM"))
            .andExpect(jsonPath("$.data[0].task_type").value("REVIEW_FOLLOW_UP"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForInvalidTimezoneOffset() throws Exception {
        when(taskApplicationService.listToday("11111111-1111-1111-1111-111111111111", "Asia/Shanghai"))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE_OFFSET", "timezone_offset must be a valid UTC offset"));

        mockMvc.perform(get("/api/v1/tasks/today")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("timezone_offset", "Asia/Shanghai"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_TIMEZONE_OFFSET"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void completesTaskWithEnvelope() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(taskApplicationService.complete(eq(taskId.toString()), eq(userId.toString()))).thenReturn(
            new TaskApplicationService.TaskCommandResult(
                new TaskApplicationService.TaskView(
                    taskId,
                    userId,
                    null,
                    TaskSource.USER,
                    "GENERAL",
                    "Done",
                    null,
                    TaskStatus.DONE,
                    0,
                    null,
                    TaskRelatedEntityType.NONE,
                    null,
                    Instant.parse("2026-03-16T01:00:00Z"),
                    Instant.parse("2026-03-16T02:00:00Z")
                ),
                "trace-2"
            )
        );

        mockMvc.perform(post("/api/v1/tasks/{taskId}/complete", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s"
                    }
                    """.formatted(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-2"))
            .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void returnsErrorEnvelopeForInvalidTaskRequest() throws Exception {
        when(taskApplicationService.skip(eq("bad-id"), any()))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_ID", "task_id must be a valid UUID"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/skip", "bad-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INVALID_TASK_ID"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
