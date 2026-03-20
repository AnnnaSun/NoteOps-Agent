package com.noteops.agent.dto.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.dto.review.ReviewTodayItemResponse;
import com.noteops.agent.dto.task.TaskResponse;
import com.noteops.agent.service.workspace.WorkspaceApplicationService;

import java.util.List;

public record WorkspaceTodayResponse(
    @JsonProperty("today_reviews")
    List<ReviewTodayItemResponse> todayReviews,
    @JsonProperty("today_tasks")
    List<TaskResponse> todayTasks
) {

    public static WorkspaceTodayResponse from(WorkspaceApplicationService.WorkspaceTodayView view) {
        return new WorkspaceTodayResponse(
            view.reviews().stream().map(ReviewTodayItemResponse::from).toList(),
            view.tasks().stream().map(TaskResponse::from).toList()
        );
    }
}
