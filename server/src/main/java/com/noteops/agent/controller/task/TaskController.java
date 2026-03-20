package com.noteops.agent.controller.task;

import com.noteops.agent.dto.task.CreateTaskRequest;
import com.noteops.agent.dto.task.TaskResponse;
import com.noteops.agent.dto.task.UpdateTaskStatusRequest;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.task.TaskApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskApplicationService taskApplicationService;

    public TaskController(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    // 创建任务，主要承接用户手动建 task 的入口。
    @PostMapping
    public ResponseEntity<ApiEnvelope<TaskResponse>> create(@RequestBody CreateTaskRequest request) {
        TaskApplicationService.TaskCommandResult result = taskApplicationService.create(
            new TaskApplicationService.CreateTaskCommand(
                request.userId(),
                request.title(),
                request.description(),
                request.taskType(),
                request.priority(),
                request.dueAt(),
                request.noteId(),
                request.relatedEntityType(),
                request.relatedEntityId()
            )
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiEnvelope.success(result.traceId(), TaskResponse.from(result.task())));
    }

    // 查询今日任务列表，供 Today 页面使用。
    @GetMapping("/today")
    public ApiEnvelope<List<TaskResponse>> today(@RequestParam("user_id") String userId,
                                                 @RequestParam(value = "timezone_offset", required = false) String timezoneOffset) {
        List<TaskResponse> tasks = taskApplicationService.listToday(userId, timezoneOffset)
            .stream()
            .map(TaskResponse::from)
            .toList();
        return ApiEnvelope.success(null, tasks);
    }

    // 将任务标记为完成，并返回最新任务状态。
    @PostMapping("/{taskId}/complete")
    public ApiEnvelope<TaskResponse> complete(@PathVariable String taskId, @RequestBody UpdateTaskStatusRequest request) {
        TaskApplicationService.TaskCommandResult result = taskApplicationService.complete(taskId, request.userId());
        return ApiEnvelope.success(result.traceId(), TaskResponse.from(result.task()));
    }

    // 将任务标记为跳过，并返回最新任务状态。
    @PostMapping("/{taskId}/skip")
    public ApiEnvelope<TaskResponse> skip(@PathVariable String taskId, @RequestBody UpdateTaskStatusRequest request) {
        TaskApplicationService.TaskCommandResult result = taskApplicationService.skip(taskId, request.userId());
        return ApiEnvelope.success(result.traceId(), TaskResponse.from(result.task()));
    }
}
