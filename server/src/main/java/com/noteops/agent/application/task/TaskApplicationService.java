package com.noteops.agent.application.task;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.application.note.NoteQueryService;
import com.noteops.agent.domain.task.TaskRelatedEntityType;
import com.noteops.agent.domain.task.TaskSource;
import com.noteops.agent.domain.task.TaskStatus;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.task.TaskRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskApplicationService {

    private static final String USER_TASK_TYPE_DEFAULT = "GENERAL";

    private final TaskRepository taskRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final Clock clock;

    @Autowired
    public TaskApplicationService(TaskRepository taskRepository,
                                  NoteRepository noteRepository,
                                  AgentTraceRepository agentTraceRepository,
                                  UserActionEventRepository userActionEventRepository) {
        this(taskRepository, noteRepository, agentTraceRepository, userActionEventRepository, Clock.systemUTC());
    }

    TaskApplicationService(TaskRepository taskRepository,
                           NoteRepository noteRepository,
                           AgentTraceRepository agentTraceRepository,
                           UserActionEventRepository userActionEventRepository,
                           Clock clock) {
        this.taskRepository = taskRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskCommandResult create(CreateTaskCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        String title = requireTitle(command.title());
        UUID noteId = parseOptionalUuid(command.noteId(), "INVALID_NOTE_ID", "note_id must be a valid UUID");
        Binding binding = resolveBinding(noteId, command.relatedEntityType(), command.relatedEntityId());
        validateNoteOwnership(userId, binding.noteId());
        int priority = normalizePriority(command.priority());
        Instant dueAt = parseOptionalInstant(command.dueAt(), "INVALID_DUE_AT", "due_at must be a valid ISO-8601 instant");

        TaskView task = taskRepository.create(
            userId,
            binding.noteId(),
            TaskSource.USER,
            defaultTaskType(command.taskType()),
            title,
            blankToNull(command.description()),
            TaskStatus.TODO,
            priority,
            dueAt,
            binding.relatedEntityType(),
            binding.relatedEntityId()
        );

        UUID traceId = agentTraceRepository.create(
            userId,
            "TASK_CREATE",
            "Create user task " + task.id(),
            "TASK",
            task.id(),
            List.of("task-worker"),
            Map.of(
                "task_id", task.id(),
                "task_source", task.taskSource().name(),
                "task_type", task.taskType()
            )
        );

        userActionEventRepository.append(
            userId,
            "TASK_CREATED",
            "TASK",
            task.id(),
            traceId,
            Map.of(
                "task_source", task.taskSource().name(),
                "task_type", task.taskType(),
                "status", task.status().name()
            )
        );

        agentTraceRepository.markCompleted(
            traceId,
            "Created task " + task.id(),
            Map.of(
                "task_id", task.id(),
                "task_source", task.taskSource().name(),
                "status", task.status().name()
            )
        );

        return new TaskCommandResult(task, traceId.toString());
    }

    public List<TaskView> listToday(String userIdRaw, String timezoneOffsetRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        ZoneOffset timezoneOffset = parseTimezoneOffset(timezoneOffsetRaw);
        return taskRepository.findTodayByUserId(userId, endOfDay(Instant.now(clock), timezoneOffset));
    }

    public List<TaskView> listUpcoming(String userIdRaw, String timezoneOffsetRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        ZoneOffset timezoneOffset = parseTimezoneOffset(timezoneOffsetRaw);
        return taskRepository.findUpcomingByUserId(userId, endOfDay(Instant.now(clock), timezoneOffset));
    }

    @Transactional
    public TaskCommandResult complete(String taskIdRaw, String userIdRaw) {
        return changeStatus(taskIdRaw, userIdRaw, TaskStatus.DONE, "TASK_COMPLETE", "TASK_COMPLETED");
    }

    @Transactional
    public TaskCommandResult skip(String taskIdRaw, String userIdRaw) {
        return changeStatus(taskIdRaw, userIdRaw, TaskStatus.SKIPPED, "TASK_SKIP", "TASK_SKIPPED");
    }

    private TaskCommandResult changeStatus(String taskIdRaw,
                                           String userIdRaw,
                                           TaskStatus targetStatus,
                                           String traceEntryType,
                                           String eventType) {
        UUID taskId = parseUuid(taskIdRaw, "INVALID_TASK_ID", "task_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        TaskView task = taskRepository.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "task not found"));
        validateActionable(task, targetStatus);

        if (task.status() != targetStatus) {
            taskRepository.updateStatus(taskId, targetStatus);
        }
        TaskView updated = taskRepository.findByIdAndUserId(taskId, userId).orElseThrow();

        UUID traceId = agentTraceRepository.create(
            userId,
            traceEntryType,
            targetStatus == TaskStatus.DONE ? "Complete task " + taskId : "Skip task " + taskId,
            "TASK",
            taskId,
            List.of("task-worker"),
            Map.of(
                "task_id", taskId,
                "from_status", task.status().name(),
                "to_status", updated.status().name()
            )
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_source", updated.taskSource().name());
        payload.put("task_type", updated.taskType());
        payload.put("status", updated.status().name());
        if (updated.relatedEntityType() != TaskRelatedEntityType.NONE && updated.relatedEntityId() != null) {
            payload.put("related_entity_type", updated.relatedEntityType().name());
            payload.put("related_entity_id", updated.relatedEntityId());
        }
        userActionEventRepository.append(userId, eventType, "TASK", taskId, traceId, payload);

        agentTraceRepository.markCompleted(
            traceId,
            targetStatus == TaskStatus.DONE ? "Completed task " + taskId : "Skipped task " + taskId,
            Map.of(
                "task_id", taskId,
                "status", updated.status().name()
            )
        );

        return new TaskCommandResult(updated, traceId.toString());
    }

    private void validateActionable(TaskView task, TaskStatus targetStatus) {
        if (task.status() == targetStatus) {
            return;
        }
        if (task.status() == TaskStatus.DONE || task.status() == TaskStatus.SKIPPED || task.status() == TaskStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_NOT_ACTIONABLE", "task is already in terminal status");
        }
    }

    private void validateNoteOwnership(UUID userId, UUID noteId) {
        if (noteId == null) {
            return;
        }
        noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
    }

    private Binding resolveBinding(UUID noteId, String relatedEntityTypeRaw, String relatedEntityIdRaw) {
        TaskRelatedEntityType relatedEntityType = parseRelatedEntityType(relatedEntityTypeRaw, noteId);
        UUID relatedEntityId = parseRelatedEntityId(relatedEntityType, relatedEntityIdRaw, noteId);
        UUID effectiveNoteId = noteId;

        if (relatedEntityType == TaskRelatedEntityType.NOTE) {
            effectiveNoteId = relatedEntityId;
        }

        return new Binding(effectiveNoteId, relatedEntityType, relatedEntityId);
    }

    private TaskRelatedEntityType parseRelatedEntityType(String rawValue, UUID noteId) {
        if (rawValue == null || rawValue.isBlank()) {
            return noteId == null ? TaskRelatedEntityType.NONE : TaskRelatedEntityType.NOTE;
        }
        try {
            return TaskRelatedEntityType.valueOf(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RELATED_ENTITY_TYPE", "related_entity_type is invalid");
        }
    }

    private UUID parseRelatedEntityId(TaskRelatedEntityType relatedEntityType, String rawValue, UUID noteId) {
        if (relatedEntityType == TaskRelatedEntityType.NONE) {
            if (rawValue != null && !rawValue.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RELATED_ENTITY_ID_NOT_ALLOWED", "related_entity_id must be omitted when related_entity_type is NONE");
            }
            return null;
        }
        if (relatedEntityType == TaskRelatedEntityType.NOTE) {
            if ((rawValue == null || rawValue.isBlank()) && noteId != null) {
                return noteId;
            }
            UUID relatedEntityId = parseOptionalUuid(rawValue, "INVALID_RELATED_ENTITY_ID", "related_entity_id must be a valid UUID");
            if (relatedEntityId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_RELATED_ENTITY_ID", "related_entity_id is required");
            }
            if (noteId != null && !noteId.equals(relatedEntityId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_BINDING_MISMATCH", "note_id and related_entity_id must match when related_entity_type is NOTE");
            }
            return relatedEntityId;
        }
        UUID relatedEntityId = parseOptionalUuid(rawValue, "INVALID_RELATED_ENTITY_ID", "related_entity_id must be a valid UUID");
        if (relatedEntityId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_RELATED_ENTITY_ID", "related_entity_id is required");
        }
        return relatedEntityId;
    }

    private String requireTitle(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_TASK_TITLE", "title is required");
        }
        return rawValue.trim();
    }

    private String defaultTaskType(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? USER_TASK_TYPE_DEFAULT : rawValue.trim();
    }

    private int normalizePriority(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRIORITY", "priority must be greater than or equal to 0");
        }
        return value;
    }

    private Instant parseOptionalInstant(String rawValue, String errorCode, String message) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private UUID parseOptionalUuid(String rawValue, String errorCode, String message) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return parseUuid(rawValue, errorCode, message);
    }

    private ZoneOffset parseTimezoneOffset(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneOffset.of(rawValue.trim());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE_OFFSET", "timezone_offset must be a valid UTC offset");
        }
    }

    private Instant endOfDay(Instant now, ZoneOffset timezoneOffset) {
        return LocalDate.ofInstant(now, timezoneOffset)
            .plusDays(1)
            .atStartOfDay()
            .minusNanos(1)
            .toInstant(timezoneOffset);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Binding(UUID noteId, TaskRelatedEntityType relatedEntityType, UUID relatedEntityId) {
    }

    public record CreateTaskCommand(String userId,
                                    String title,
                                    String description,
                                    String taskType,
                                    Integer priority,
                                    String dueAt,
                                    String noteId,
                                    String relatedEntityType,
                                    String relatedEntityId) {
    }

    public record TaskCommandResult(TaskView task, String traceId) {
    }

    public record TaskView(UUID id,
                           UUID userId,
                           UUID noteId,
                           TaskSource taskSource,
                           String taskType,
                           String title,
                           String description,
                           TaskStatus status,
                           int priority,
                           Instant dueAt,
                           TaskRelatedEntityType relatedEntityType,
                           UUID relatedEntityId,
                           Instant createdAt,
                           Instant updatedAt) {
    }
}
