package com.noteops.agent.dto.idea;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.dto.task.TaskResponse;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.service.task.TaskApplicationService;

import java.util.List;

public record IdeaTaskGenerationResponse(
    @JsonProperty("idea_id")
    String ideaId,
    String status,
    @JsonProperty("generated_tasks")
    List<TaskResponse> generatedTasks
) {

    public static IdeaTaskGenerationResponse from(IdeaRepository.IdeaRecord idea,
                                                  List<TaskApplicationService.TaskView> tasks) {
        return new IdeaTaskGenerationResponse(
            idea.id().toString(),
            idea.status().name(),
            tasks.stream().map(TaskResponse::from).toList()
        );
    }
}
