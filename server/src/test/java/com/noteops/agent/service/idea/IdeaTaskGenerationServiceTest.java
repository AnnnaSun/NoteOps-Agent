package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.model.task.TaskRelatedEntityType;
import com.noteops.agent.model.task.TaskSource;
import com.noteops.agent.model.task.TaskStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.task.TaskRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.task.TaskApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeaTaskGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-08T18:00:00Z");

    @Test
    void generatesSystemTasksFromAssessedIdeaAndPromotesIdeaToPlanned() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.FROM_NOTE,
            noteId,
            "Idea from note",
            "Build a note-to-action workflow",
            IdeaStatus.ASSESSED,
            new IdeaAssessmentResult(
                "Problem statement",
                "Target user",
                "Core hypothesis",
                List.of("Validation path"),
                List.of("Interview 3 users", "Prototype the follow-up loop"),
                List.of("Risk"),
                "Reasoning summary"
            ),
            NOW,
            NOW
        ));
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryAgentTraceRepository traceRepository = new InMemoryAgentTraceRepository();
        InMemoryUserActionEventRepository eventRepository = new InMemoryUserActionEventRepository();
        IdeaTaskGenerationService service = new IdeaTaskGenerationService(
            ideaRepository,
            taskRepository,
            traceRepository,
            eventRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        IdeaTaskGenerationService.IdeaTaskGenerationResult result = service.generate(
            new IdeaTaskGenerationService.GenerateIdeaTasksCommand(ideaId.toString(), userId.toString())
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.idea().status()).isEqualTo(IdeaStatus.PLANNED);
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks()).allSatisfy(task -> {
            assertThat(task.taskSource()).isEqualTo(TaskSource.SYSTEM);
            assertThat(task.taskType()).isEqualTo("IDEA_NEXT_ACTION");
            assertThat(task.status()).isEqualTo(TaskStatus.TODO);
            assertThat(task.relatedEntityType()).isEqualTo(TaskRelatedEntityType.IDEA);
            assertThat(task.relatedEntityId()).isEqualTo(ideaId);
            assertThat(task.noteId()).isEqualTo(noteId);
            assertThat(task.dueAt()).isNotNull();
        });
        assertThat(result.tasks().get(0).dueAt()).isEqualTo(NOW);
        assertThat(result.tasks().get(1).dueAt()).isEqualTo(NOW.plusSeconds(86_400));
        assertThat(traceRepository.entryTypes).containsExactly("IDEA_TASK_GENERATE");
        assertThat(eventRepository.eventTypes).containsExactly("IDEA_TASKS_GENERATED");
        Instant endOfDay = Instant.parse("2026-04-08T23:59:59Z");
        assertThat(taskRepository.findTodayByUserId(userId, endOfDay)).hasSize(1);
        assertThat(taskRepository.findUpcomingByUserId(userId, endOfDay)).hasSize(1);
    }

    @Test
    void rejectsTaskGenerationWhenIdeaWasAlreadyClaimedByAnotherRequest() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Assessed idea",
            null,
            IdeaStatus.ASSESSED,
            new IdeaAssessmentResult(
                "Problem statement",
                "Target user",
                "Core hypothesis",
                List.of("Validation path"),
                List.of("Next action"),
                List.of(),
                "Reasoning summary"
            ),
            NOW,
            NOW
        ));
        ideaRepository.failNextStatusClaim();

        IdeaTaskGenerationService service = new IdeaTaskGenerationService(
            ideaRepository,
            new InMemoryTaskRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.generate(
            new IdeaTaskGenerationService.GenerateIdeaTasksCommand(ideaId.toString(), userId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("idea task generation already started");
    }

    @Test
    void rejectsIdeaThatIsNotAssessed() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Captured idea",
            null,
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty(),
            NOW,
            NOW
        ));

        IdeaTaskGenerationService service = new IdeaTaskGenerationService(
            ideaRepository,
            new InMemoryTaskRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.generate(
            new IdeaTaskGenerationService.GenerateIdeaTasksCommand(ideaId.toString(), userId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("only ASSESSED ideas can generate tasks");
    }

    @Test
    void rejectsAssessedIdeaWithoutNextActions() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.store(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Assessed idea",
            null,
            IdeaStatus.ASSESSED,
            new IdeaAssessmentResult(
                "Problem statement",
                "Target user",
                "Core hypothesis",
                List.of("Validation path"),
                List.of(),
                List.of(),
                "Reasoning summary"
            ),
            NOW,
            NOW
        ));

        IdeaTaskGenerationService service = new IdeaTaskGenerationService(
            ideaRepository,
            new InMemoryTaskRepository(),
            new InMemoryAgentTraceRepository(),
            new InMemoryUserActionEventRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.generate(
            new IdeaTaskGenerationService.GenerateIdeaTasksCommand(ideaId.toString(), userId.toString())
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("assessment_result.next_actions must contain at least one item");
    }

    private static final class InMemoryIdeaRepository implements IdeaRepository {

        private final Map<UUID, IdeaRecord> ideas = new HashMap<>();
        private boolean failNextStatusClaim;

        void store(IdeaRecord record) {
            ideas.put(record.id(), record);
        }

        void failNextStatusClaim() {
            this.failNextStatusClaim = true;
        }

        @Override
        public IdeaRecord create(UUID userId, IdeaSourceMode sourceMode, UUID sourceNoteId, String title, String rawDescription, IdeaStatus status, IdeaAssessmentResult assessmentResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
            IdeaRecord record = ideas.get(ideaId);
            if (record == null || !record.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public List<IdeaRecord> findAllByUserId(UUID userId) {
            return ideas.values().stream()
                .filter(record -> record.userId().equals(userId))
                .toList();
        }

        @Override
        public IdeaRecord updateAssessment(UUID ideaId, UUID userId, IdeaAssessmentResult assessmentResult, IdeaStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdeaRecord updateStatus(UUID ideaId, UUID userId, IdeaStatus status) {
            IdeaRecord current = findByIdAndUserId(ideaId, userId).orElseThrow();
            IdeaRecord updated = new IdeaRecord(
                current.id(),
                current.userId(),
                current.sourceMode(),
                current.sourceNoteId(),
                current.title(),
                current.rawDescription(),
                status,
                current.assessmentResult(),
                current.createdAt(),
                NOW
            );
            ideas.put(ideaId, updated);
            return updated;
        }

        @Override
        public Optional<IdeaRecord> updateStatusIfCurrent(UUID ideaId, UUID userId, IdeaStatus expectedStatus, IdeaStatus targetStatus) {
            if (failNextStatusClaim) {
                failNextStatusClaim = false;
                return Optional.empty();
            }
            IdeaRecord current = findByIdAndUserId(ideaId, userId).orElseThrow();
            if (current.status() != expectedStatus) {
                return Optional.empty();
            }
            return Optional.of(updateStatus(ideaId, userId, targetStatus));
        }
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
            UUID taskId = UUID.randomUUID();
            TaskApplicationService.TaskView task = new TaskApplicationService.TaskView(
                taskId,
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
            tasks.put(taskId, task);
            return task;
        }

        @Override
        public List<TaskApplicationService.TaskView> findTodayByUserId(UUID userId, Instant dueAtInclusive) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .filter(task -> task.dueAt() == null || !task.dueAt().isAfter(dueAtInclusive))
                .toList();
        }

        @Override
        public List<TaskApplicationService.TaskView> findUpcomingByUserId(UUID userId, Instant dueAfterExclusive) {
            return tasks.values().stream()
                .filter(task -> task.userId().equals(userId))
                .filter(task -> task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS)
                .filter(task -> task.dueAt() != null && task.dueAt().isAfter(dueAfterExclusive))
                .toList();
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findByIdAndUserId(UUID taskId, UUID userId) {
            TaskApplicationService.TaskView task = tasks.get(taskId);
            if (task == null || !task.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(task);
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findOpenByUserIdAndSourceAndTaskTypeAndNoteId(UUID userId, TaskSource taskSource, String taskType, UUID noteId) {
            return Optional.empty();
        }

        @Override
        public Optional<TaskApplicationService.TaskView> findOpenDuplicateUserTask(UUID userId, String title, String taskType, UUID noteId, TaskRelatedEntityType relatedEntityType, UUID relatedEntityId) {
            return Optional.empty();
        }

        @Override
        public void updateStatus(UUID taskId, TaskStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refreshOpenTask(UUID taskId, String title, String description, int priority, Instant dueAt, TaskRelatedEntityType relatedEntityType, UUID relatedEntityId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryAgentTraceRepository implements AgentTraceRepository {

        private final List<String> entryTypes = new ArrayList<>();

        @Override
        public UUID create(UUID userId, String entryType, String goal, String rootEntityType, UUID rootEntityId, List<String> workerSequence, Map<String, Object> orchestratorState) {
            entryTypes.add(entryType);
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

        private final List<String> eventTypes = new ArrayList<>();

        @Override
        public void append(UUID userId, String eventType, String entityType, UUID entityId, UUID traceId, Map<String, Object> payload) {
            eventTypes.add(eventType);
        }
    }
}
