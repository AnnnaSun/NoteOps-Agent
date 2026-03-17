package com.noteops.agent.application.workspace;

import com.noteops.agent.application.review.ReviewApplicationService;
import com.noteops.agent.application.task.TaskApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkspaceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceApplicationService.class);

    private final ReviewApplicationService reviewApplicationService;
    private final TaskApplicationService taskApplicationService;

    public WorkspaceApplicationService(ReviewApplicationService reviewApplicationService,
                                       TaskApplicationService taskApplicationService) {
        this.reviewApplicationService = reviewApplicationService;
        this.taskApplicationService = taskApplicationService;
    }

    public WorkspaceTodayView today(String userId, String timezoneOffset) {
        log.info("action=workspace_today_query_start user_id={} timezone_offset={}", userId, timezoneOffset);
        List<ReviewApplicationService.ReviewTodayItemView> reviews = reviewApplicationService.listToday(userId);
        List<TaskApplicationService.TaskView> tasks = taskApplicationService.listToday(userId, timezoneOffset);
        log.info(
            "action=workspace_today_query_success user_id={} timezone_offset={} review_count={} task_count={}",
            userId,
            timezoneOffset,
            reviews.size(),
            tasks.size()
        );
        return new WorkspaceTodayView(reviews, tasks);
    }

    public WorkspaceUpcomingView upcoming(String userId, String timezoneOffset) {
        log.info("action=workspace_upcoming_query_start user_id={} timezone_offset={}", userId, timezoneOffset);
        List<ReviewApplicationService.ReviewTodayItemView> reviews = reviewApplicationService.listUpcoming(userId, timezoneOffset);
        List<TaskApplicationService.TaskView> tasks = taskApplicationService.listUpcoming(userId, timezoneOffset);
        log.info(
            "action=workspace_upcoming_query_success user_id={} timezone_offset={} review_count={} task_count={}",
            userId,
            timezoneOffset,
            reviews.size(),
            tasks.size()
        );
        return new WorkspaceUpcomingView(reviews, tasks);
    }

    public record WorkspaceTodayView(
        List<ReviewApplicationService.ReviewTodayItemView> reviews,
        List<TaskApplicationService.TaskView> tasks
    ) {
    }

    public record WorkspaceUpcomingView(
        List<ReviewApplicationService.ReviewTodayItemView> reviews,
        List<TaskApplicationService.TaskView> tasks
    ) {
    }
}
