package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.task.TaskRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.task.TaskApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdeaTaskGenerationService {

    private static final Logger log = LoggerFactory.getLogger(IdeaTaskGenerationService.class);

    private static final String TASK_TYPE = "IDEA_NEXT_ACTION";
    private static final int SYSTEM_TASK_PRIORITY = 80;

    private final IdeaRepository ideaRepository;
    private final TaskRepository taskRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final Clock clock;

    @Autowired
    public IdeaTaskGenerationService(IdeaRepository ideaRepository,
                                     TaskRepository taskRepository,
                                     AgentTraceRepository agentTraceRepository,
                                     UserActionEventRepository userActionEventRepository) {
        this(
            ideaRepository,
            taskRepository,
            agentTraceRepository,
            userActionEventRepository,
            Clock.systemUTC()
        );
    }

    IdeaTaskGenerationService(IdeaRepository ideaRepository,
                              TaskRepository taskRepository,
                              AgentTraceRepository agentTraceRepository,
                              UserActionEventRepository userActionEventRepository,
                              Clock clock) {
        this.ideaRepository = ideaRepository;
        this.taskRepository = taskRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.clock = clock;
    }

    @Transactional
    // 从 assessed idea 显式派生 system tasks，并将 Idea 推进到 PLANNED。
    public IdeaTaskGenerationResult generate(GenerateIdeaTasksCommand command) {
        UUID ideaId = parseUuid(command.ideaId(), "INVALID_IDEA_ID", "idea_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        IdeaRepository.IdeaRecord idea = ideaRepository.findByIdAndUserId(ideaId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND", "idea not found"));
        List<String> nextActions = materializeNextActions(idea);

        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("idea_id", idea.id());
        traceState.put("from_status", idea.status().name());
        traceState.put("to_status", IdeaStatus.PLANNED.name());
        traceState.put("next_action_count", nextActions.size());

        UUID traceId = agentTraceRepository.create(
            userId,
            "IDEA_TASK_GENERATE",
            "Generate tasks from idea " + idea.id(),
            "IDEA",
            idea.id(),
            List.of("idea-task-worker"),
            traceState
        );
        log.info(
            "module=IdeaTaskGenerationService action=idea_task_generate_start trace_id={} user_id={} idea_id={} from_status={} next_action_count={} result=RUNNING",
            traceId,
            userId,
            idea.id(),
            idea.status().name(),
            nextActions.size()
        );

        try {
            IdeaRepository.IdeaRecord plannedIdea = ideaRepository.updateStatusIfCurrent(
                    idea.id(),
                    userId,
                    IdeaStatus.ASSESSED,
                    IdeaStatus.PLANNED
                )
                .orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEA_TASK_GENERATION_ALREADY_STARTED",
                    "idea task generation already started"
                ));

            List<TaskApplicationService.TaskView> tasks = new ArrayList<>();
            Instant schedulingBase = Instant.now(clock);
            for (int index = 0; index < nextActions.size(); index++) {
                String nextAction = nextActions.get(index);
                tasks.add(taskRepository.create(
                    userId,
                    idea.sourceNoteId(),
                    TaskSource.SYSTEM,
                    TASK_TYPE,
                    nextAction,
                    buildTaskDescription(idea, nextAction),
                    TaskStatus.TODO,
                    SYSTEM_TASK_PRIORITY,
                    scheduleDueAt(schedulingBase, index),
                    TaskRelatedEntityType.IDEA,
                    idea.id()
                ));
            }

            userActionEventRepository.append(
                userId,
                "IDEA_TASKS_GENERATED",
                "IDEA",
                plannedIdea.id(),
                traceId,
                Map.of(
                    "generated_task_count", tasks.size(),
                    "from_status", idea.status().name(),
                    "to_status", plannedIdea.status().name()
                )
            );

            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("generated_task_count", tasks.size());
            completedState.put("result", "COMPLETED");
            agentTraceRepository.markCompleted(traceId, "Generated tasks from idea " + plannedIdea.id(), completedState);
            log.info(
                "module=IdeaTaskGenerationService action=idea_task_generate_success trace_id={} user_id={} idea_id={} generated_task_count={} to_status={} result=COMPLETED",
                traceId,
                userId,
                plannedIdea.id(),
                tasks.size(),
                plannedIdea.status().name()
            );
            return new IdeaTaskGenerationResult(plannedIdea, tasks, traceId.toString());
        } catch (Exception exception) {
            Map<String, Object> failedState = new LinkedHashMap<>(traceState);
            failedState.put("result", "FAILED");
            failedState.put("error_message", exception.getMessage());
            agentTraceRepository.markFailed(traceId, "Failed to generate tasks from idea " + idea.id(), failedState);
            userActionEventRepository.append(
                userId,
                "IDEA_TASK_GENERATION_FAILED",
                "IDEA",
                idea.id(),
                traceId,
                Map.of("error_message", exception.getMessage())
            );
            log.warn(
                "module=IdeaTaskGenerationService action=idea_task_generate_fail trace_id={} user_id={} idea_id={} result=FAILED error_code=IDEA_TASK_GENERATION_FAILED error_message={}",
                traceId,
                userId,
                idea.id(),
                exception.getMessage()
            );
            throw exception;
        }
    }

    private List<String> materializeNextActions(IdeaRepository.IdeaRecord idea) {
        if (idea.status() != IdeaStatus.ASSESSED) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEA_STATUS_NOT_TASK_READY", "only ASSESSED ideas can generate tasks");
        }
        IdeaAssessmentResult assessmentResult = idea.assessmentResult();
        if (assessmentResult == null || assessmentResult.nextActions() == null) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "IDEA_NEXT_ACTIONS_REQUIRED",
                "assessment_result.next_actions must contain at least one item"
            );
        }
        List<String> nextActions = new ArrayList<>(new LinkedHashSet<>(assessmentResult.nextActions().stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList()));
        if (nextActions.isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "IDEA_NEXT_ACTIONS_REQUIRED",
                "assessment_result.next_actions must contain at least one item"
            );
        }
        return nextActions;
    }

    private String buildTaskDescription(IdeaRepository.IdeaRecord idea, String nextAction) {
        List<String> parts = new ArrayList<>();
        parts.add("Derived from idea: " + idea.title());
        if (idea.assessmentResult().problemStatement() != null) {
            parts.add("Problem: " + idea.assessmentResult().problemStatement());
        }
        parts.add("Action: " + nextAction);
        return String.join("\n", parts);
    }

    private Instant scheduleDueAt(Instant schedulingBase, int index) {
        if (index <= 0) {
            return schedulingBase;
        }
        return schedulingBase.plusSeconds(86_400L * index);
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public record GenerateIdeaTasksCommand(
        String ideaId,
        String userId
    ) {
    }

    public record IdeaTaskGenerationResult(
        IdeaRepository.IdeaRecord idea,
        List<TaskApplicationService.TaskView> tasks,
        String traceId
    ) {
    }
}
