package com.noteops.agent.dto.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.dto.review.ReviewTodayItemResponse;
import com.noteops.agent.dto.task.TaskResponse;
import com.noteops.agent.service.workspace.WorkspaceApplicationService;

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
