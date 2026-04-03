package com.noteops.agent.service.workspace;

import com.noteops.agent.service.review.ReviewApplicationService;
import com.noteops.agent.service.task.TaskApplicationService;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceApplicationServiceTest {

    @Test
    void aggregatesTodayReviewsAndTasks() {
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        WorkspaceApplicationService service = new WorkspaceApplicationService(reviewApplicationService, taskApplicationService);

        when(reviewApplicationService.listToday("11111111-1111-1111-1111-111111111111")).thenReturn(List.of(
            new ReviewApplicationService.ReviewTodayItemView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReviewQueueType.RECALL,
                ReviewCompletionStatus.PARTIAL,
                null,
                BigDecimal.valueOf(30),
                Instant.parse("2026-03-16T01:00:00Z"),
                24,
                1,
                "Recall note",
                "summary",
                List.of("point"),
                List.of("tag-1"),
                null,
                List.of(),
                null
            )
        ));
        when(taskApplicationService.listToday("11111111-1111-1111-1111-111111111111", "+08:00")).thenReturn(List.of(
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
        ));

        WorkspaceApplicationService.WorkspaceTodayView result = service.today("11111111-1111-1111-1111-111111111111", "+08:00");

        assertThat(result.reviews()).hasSize(1);
        assertThat(result.tasks()).hasSize(1);
    }

    @Test
    void aggregatesUpcomingReviewsAndTasks() {
        ReviewApplicationService reviewApplicationService = mock(ReviewApplicationService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        WorkspaceApplicationService service = new WorkspaceApplicationService(reviewApplicationService, taskApplicationService);

        when(reviewApplicationService.listUpcoming("11111111-1111-1111-1111-111111111111", "+08:00")).thenReturn(List.of());
        when(taskApplicationService.listUpcoming("11111111-1111-1111-1111-111111111111", "+08:00")).thenReturn(List.of());

        WorkspaceApplicationService.WorkspaceUpcomingView result = service.upcoming("11111111-1111-1111-1111-111111111111", "+08:00");

        assertThat(result.reviews()).isEmpty();
        assertThat(result.tasks()).isEmpty();
    }
}
