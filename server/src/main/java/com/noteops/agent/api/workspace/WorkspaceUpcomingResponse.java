package com.noteops.agent.api.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.api.review.ReviewTodayItemResponse;
import com.noteops.agent.api.task.TaskResponse;
import com.noteops.agent.application.workspace.WorkspaceApplicationService;

import java.util.List;

public record WorkspaceUpcomingResponse(
    @JsonProperty("upcoming_reviews")
    List<ReviewTodayItemResponse> upcomingReviews,
    @JsonProperty("upcoming_tasks")
    List<TaskResponse> upcomingTasks
) {

    public static WorkspaceUpcomingResponse from(WorkspaceApplicationService.WorkspaceUpcomingView view) {
        return new WorkspaceUpcomingResponse(
            view.reviews().stream().map(ReviewTodayItemResponse::from).toList(),
            view.tasks().stream().map(TaskResponse::from).toList()
        );
    }
}
