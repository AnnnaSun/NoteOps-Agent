package com.noteops.agent.service.task;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.task.TaskRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-16T01:00:00Z");
    private static final Instant LATE_DAY = Instant.parse("2026-03-16T10:00:00Z");

    @Test
    void createsStandaloneUserTask() {
        UUID userId = UUID.randomUUID();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        TaskApplicationService service = newService(taskRepository, new InMemoryNoteRepository());

        TaskApplicationService.TaskCommandResult result = service.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Write summary",
                "Capture the meeting outcome.",
                null,
                3,
                null,
                null,
                null,
                null
            )
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.task().taskSource()).isEqualTo(TaskSource.USER);
        assertThat(result.task().taskType()).isEqualTo("GENERAL");
        assertThat(result.task().relatedEntityType()).isEqualTo(TaskRelatedEntityType.NONE);
        assertThat(result.task().status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void bindsUserTaskToNoteByDefaultWhenNoteIdIsProvided() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(noteSummary(userId, noteId, "Bound note"));
        TaskApplicationService service = newService(new InMemoryTaskRepository(), noteRepository);

        TaskApplicationService.TaskCommandResult result = service.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Review note",
                null,
                "NOTE_ACTION",
                null,
                "2026-03-16T06:00:00Z",
                noteId.toString(),
                null,
                null
            )
        );

        assertThat(result.task().noteId()).isEqualTo(noteId);
        assertThat(result.task().relatedEntityType()).isEqualTo(TaskRelatedEntityType.NOTE);
        assertThat(result.task().relatedEntityId()).isEqualTo(noteId);
    }

    @Test
    void rejectsDuplicateOpenUserTaskForSameBinding() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.store(noteSummary(userId, noteId, "Bound note"));
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        TaskApplicationService service = newService(taskRepository, noteRepository);

        TaskApplicationService.CreateTaskCommand command = new TaskApplicationService.CreateTaskCommand(
            userId.toString(),
            "跟进这次 review",
            null,
            "REVIEW_ACTION",
            null,
            null,
            noteId.toString(),
            "NOTE",
            noteId.toString()
        );

        service.create(command);

        assertThatThrownBy(() -> service.create(command))
            .isInstanceOf(ApiException.class)
            .hasMessage("an open user task with the same title and binding already exists");
        assertThat(taskRepository.tasks).hasSize(1);
    }

    @Test
    void returnsTodayTasksUsingProvidedTimezoneOffset() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        taskRepository.create(userId, noteId, TaskSource.SYSTEM, "REVIEW_FOLLOW_UP", "System first", null,
            TaskStatus.TODO, 90, NOW.plusSeconds(3600), TaskRelatedEntityType.REVIEW, UUID.randomUUID());
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "User later", null,
            TaskStatus.TODO, 1, NOW.plusSeconds(7200), TaskRelatedEntityType.NONE, null);
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Tomorrow", null,
            TaskStatus.TODO, 1, Instant.parse("2026-03-17T05:00:00Z"), TaskRelatedEntityType.NONE, null);

        TaskApplicationService service = newService(taskRepository, new InMemoryNoteRepository(), LATE_DAY);

        List<TaskApplicationService.TaskView> tasks = service.listToday(userId.toString(), "-08:00");

        assertThat(tasks).extracting(TaskApplicationService.TaskView::title)
            .containsExactly("System first", "User later", "Tomorrow");
    }

    @Test
    void keepsUtcCompatibilityWhenOffsetIsOmitted() {
        UUID userId = UUID.randomUUID();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Today UTC", null,
            TaskStatus.TODO, 1, NOW.plusSeconds(7200), TaskRelatedEntityType.NONE, null);
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Tomorrow UTC", null,
            TaskStatus.TODO, 1, Instant.parse("2026-03-17T05:00:00Z"), TaskRelatedEntityType.NONE, null);

        TaskApplicationService service = newService(taskRepository, new InMemoryNoteRepository());

        List<TaskApplicationService.TaskView> tasks = service.listToday(userId.toString(), null);

        assertThat(tasks).extracting(TaskApplicationService.TaskView::title)
            .containsExactly("Today UTC");
    }

    @Test
    void returnsUpcomingTasksAfterTodayBoundary() {
        UUID userId = UUID.randomUUID();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Later today", null,
            TaskStatus.TODO, 1, Instant.parse("2026-03-16T08:00:00Z"), TaskRelatedEntityType.NONE, null);
        taskRepository.create(userId, null, TaskSource.SYSTEM, "REVIEW_FOLLOW_UP", "Tomorrow system", null,
            TaskStatus.TODO, 90, Instant.parse("2026-03-17T02:00:00Z"), TaskRelatedEntityType.REVIEW, UUID.randomUUID());
        taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Tomorrow user", null,
            TaskStatus.TODO, 1, Instant.parse("2026-03-17T03:00:00Z"), TaskRelatedEntityType.NONE, null);

        TaskApplicationService service = newService(taskRepository, new InMemoryNoteRepository(), LATE_DAY);

        List<TaskApplicationService.TaskView> tasks = service.listUpcoming(userId.toString(), "+08:00");

        assertThat(tasks).extracting(TaskApplicationService.TaskView::title)
            .containsExactly("Tomorrow system", "Tomorrow user");
    }

    @Test
    void rejectsInvalidTimezoneOffset() {
        UUID userId = UUID.randomUUID();
        TaskApplicationService service = newService(new InMemoryTaskRepository(), new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.listToday(userId.toString(), "Asia/Shanghai"))
            .isInstanceOf(ApiException.class)
            .hasMessage("timezone_offset must be a valid UTC offset");
    }

    @Test
    void completesTaskAndWritesTerminalStatus() {
        UUID userId = UUID.randomUUID();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        TaskApplicationService.TaskView task = taskRepository.create(userId, null, TaskSource.USER, "GENERAL", "Do it", null,
            TaskStatus.TODO, 0, null, TaskRelatedEntityType.NONE, null);

        TaskApplicationService service = newService(taskRepository, new InMemoryNoteRepository());

        TaskApplicationService.TaskCommandResult result = service.complete(task.id().toString(), userId.toString());

        assertThat(result.task().status()).isEqualTo(TaskStatus.DONE);
        assertThat(taskRepository.findByIdAndUserId(task.id(), userId)).get()
            .extracting(TaskApplicationService.TaskView::status)
            .isEqualTo(TaskStatus.DONE);
    }

    @Test
    void rejectsMismatchedNoteBinding() {
        UUID userId = UUID.randomUUID();
        TaskApplicationService service = newService(new InMemoryTaskRepository(), new InMemoryNoteRepository());

        assertThatThrownBy(() -> service.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Bad binding",
                null,
                null,
                null,
                null,
                UUID.randomUUID().toString(),
                "NOTE",
                UUID.randomUUID().toString()
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("note_id and related_entity_id must match when related_entity_type is NOTE");
    }

    private TaskApplicationService newService(InMemoryTaskRepository taskRepository, InMemoryNoteRepository noteRepository) {
        return newService(taskRepository, noteRepository, NOW);
    }

    private TaskApplicationService newService(InMemoryTaskRepository taskRepository,
                                              InMemoryNoteRepository noteRepository,
                                              Instant now) {
        return new TaskApplicationService(
            taskRepository,
            noteRepository,
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private NoteQueryService.NoteSummaryView noteSummary(UUID userId, UUID noteId, String title) {
        return new NoteQueryService.NoteSummaryView(
            noteId,
            userId,
            title,
            "summary",
            List.of("point"),
            List.of("tag"),
            UUID.randomUUID(),
            NOW
        );
    }

    private static final class InMemoryTaskRepository implements TaskRepository {

        private final Map<UUID, TaskApplicationService.TaskView> tasks = new HashMap<>();

        @Override
        public TaskApplicationService.TaskView create(UUID userId,
                                                      UUID noteId,
                                                      TaskSource taskSource,
                                                      String taskType,
                                                      String title,
                                                      String description,
                                                      TaskStatus status,
                                                      int priority,
                                                      Instant dueAt,
                                                      TaskRelatedEntityType relatedEntityType,
                                                      UUID relatedEntityId) {
            UUID id = UUID.randomUUID();
            TaskApplicationService.TaskView view = new TaskApplicationService.TaskView(
                id,
                userId,
                noteId,
                taskSource,
                taskType,
                title,
                description,
                status,
                priority,
                dueAt,
                relatedEntityType,
                relatedEntityId,
                NOW,
                NOW
            );
            tasks.put(id, view);
            return view;
        }

        @Override
        public List<TaskApplicationService.TaskView> findTodayByUserId(UUID userId, Instant dueAtInclusive) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .filter(task -> task.dueAt() == null || !task.dueAt().isAfter(dueAtInclusive))
                .sorted(Comparator
                    .comparing((TaskApplicationService.TaskView task) -> task.taskSource() == TaskSource.SYSTEM ? 0 : 1)
                    .thenComparing(task -> task.dueAt() == null ? Instant.MAX : task.dueAt())
                    .thenComparing(TaskApplicationService.TaskView::priority, Comparator.reverseOrder())
                    .thenComparing(TaskApplicationService.TaskView::createdAt))
                .toList();
        }

        @Override
        public List<TaskApplicationService.TaskView> findUpcomingByUserId(UUID userId, Instant dueAfterExclusive) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .filter(task -> task.dueAt() != null && task.dueAt().isAfter(dueAfterExclusive))
                .sorted(Comparator
                    .comparing(TaskApplicationService.TaskView::dueAt)
                    .thenComparing((TaskApplicationService.TaskView task) -> task.taskSource() == TaskSource.SYSTEM ? 0 : 1)
                    .thenComparing(TaskApplicationService.TaskView::priority, Comparator.reverseOrder())
                    .thenComparing(TaskApplicationService.TaskView::createdAt))
                .toList();
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findByIdAndUserId(UUID taskId, UUID userId) {
            return Optional.ofNullable(tasks.get(taskId))
                .filter(task -> task.userId().equals(userId));
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findOpenByUserIdAndSourceAndTaskTypeAndNoteId(UUID userId,
                                                                                                        TaskSource taskSource,
                                                                                                        String taskType,
                                                                                                        UUID noteId) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.taskSource() == taskSource)
                .filter(task -> task.taskType().equals(taskType))
                .filter(task -> noteId.equals(task.noteId()))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .findFirst();
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findOpenDuplicateUserTask(UUID userId,
                                                                                   String title,
                                                                                   String taskType,
                                                                                   UUID noteId,
                                                                                   TaskRelatedEntityType relatedEntityType,
                                                                                   UUID relatedEntityId) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.taskSource() == TaskSource.USER)
                .filter(task -> task.title().equals(title))
                .filter(task -> task.taskType().equals(taskType))
                .filter(task -> task.relatedEntityType() == relatedEntityType)
                .filter(task -> java.util.Objects.equals(task.noteId(), noteId))
                .filter(task -> java.util.Objects.equals(task.relatedEntityId(), relatedEntityId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .findFirst();
        }

        @Override
        public void updateStatus(UUID taskId, TaskStatus status) {
            TaskApplicationService.TaskView existing = tasks.get(taskId);
            tasks.put(taskId, new TaskApplicationService.TaskView(
                existing.id(),
                existing.userId(),
                existing.noteId(),
                existing.taskSource(),
                existing.taskType(),
                existing.title(),
                existing.description(),
                status,
                existing.priority(),
                existing.dueAt(),
                existing.relatedEntityType(),
                existing.relatedEntityId(),
                existing.createdAt(),
                NOW
            ));
        }

        @Override
        public void refreshOpenTask(UUID taskId,
                                    String title,
                                    String description,
                                    int priority,
                                    Instant dueAt,
                                    TaskRelatedEntityType relatedEntityType,
                                    UUID relatedEntityId) {
            TaskApplicationService.TaskView existing = tasks.get(taskId);
            tasks.put(taskId, new TaskApplicationService.TaskView(
                existing.id(),
                existing.userId(),
                existing.noteId(),
                existing.taskSource(),
                existing.taskType(),
                title,
                description,
                existing.status(),
                priority,
                dueAt,
                relatedEntityType,
                relatedEntityId,
                existing.createdAt(),
                NOW
            ));
        }
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final List<NoteQueryService.NoteSummaryView> notes = new ArrayList<>();

        @Override
        public NoteCreationResult create(UUID userId,
                                         String title,
                                         String currentSummary,
                                         List<String> currentKeyPoints,
                                         String sourceUri,
                                         String rawText,
                                         String cleanText,
                                         Map<String, Object> sourceSnapshot,
                                         Map<String, Object> analysisResult) {
            return new NoteCreationResult(UUID.randomUUID(), UUID.randomUUID());
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return notes.stream()
                .filter(note -> note.id().equals(noteId) && note.userId().equals(userId))
                .findFirst()
                .map(note -> new NoteQueryService.NoteDetailView(
                    note.id(),
                    note.userId(),
                    note.title(),
                    note.currentSummary(),
                    note.currentKeyPoints(),
                    note.latestContentId(),
                    "PRIMARY",
                    null,
                    null,
                    null,
                    NOW,
                    NOW,
                    List.of()
                ));
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return notes.stream()
                .filter(note -> note.userId().equals(userId))
                .toList();
        }

        void store(NoteQueryService.NoteSummaryView note) {
            notes.add(note);
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        @Override
        public UUID create(UUID userId,
                           String entryType,
                           String goal,
                           String rootEntityType,
                           UUID rootEntityId,
                           List<String> workerSequence,
                           Map<String, Object> orchestratorState) {
            return UUID.randomUUID();
        }

        @Override
        public void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }

        @Override
        public void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState) {
        }
    }

    private static final class InMemoryUserActionEventRepository implements UserActionEventRepository {

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
        }
    }
}
