package com.noteops.agent.persistence.task;

import com.noteops.agent.application.task.TaskApplicationService;
import com.noteops.agent.domain.task.TaskRelatedEntityType;
import com.noteops.agent.domain.task.TaskSource;
import com.noteops.agent.domain.task.TaskStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcClient jdbcClient;

    public JdbcTaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

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
        jdbcClient.sql("""
            insert into tasks (
                id, user_id, note_id, task_source, task_type, title, description, status,
                priority, due_at, related_entity_type, related_entity_id
            ) values (
                :id, :userId, :noteId, :taskSource, :taskType, :title, :description, :status,
                :priority, :dueAt, :relatedEntityType, :relatedEntityId
            )
            """)
            .param("id", taskId)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("taskSource", taskSource.name())
            .param("taskType", taskType)
            .param("title", title)
            .param("description", description)
            .param("status", status.name())
            .param("priority", priority)
            .param("dueAt", timestampOrNull(dueAt))
            .param("relatedEntityType", relatedEntityType.name())
            .param("relatedEntityId", relatedEntityId)
            .update();
        return findByIdAndUserId(taskId, userId).orElseThrow();
    }

    @Override
    public List<TaskApplicationService.TaskView> findTodayByUserId(UUID userId, Instant dueAtInclusive) {
        return jdbcClient.sql("""
            select id, user_id, note_id, task_source, task_type, title, description, status,
                   priority, due_at, related_entity_type, related_entity_id, created_at, updated_at
            from tasks
            where user_id = :userId
              and status in ('TODO', 'IN_PROGRESS')
              and (due_at is null or due_at <= :dueAtInclusive)
            order by case when task_source = 'SYSTEM' then 0 else 1 end,
                     case when due_at is null then 1 else 0 end,
                     due_at asc,
                     priority desc,
                     created_at asc
            """)
            .param("userId", userId)
            .param("dueAtInclusive", Timestamp.from(dueAtInclusive))
            .query((rs, rowNum) -> mapView(rs))
            .list();
    }

    @Override
    public List<TaskApplicationService.TaskView> findUpcomingByUserId(UUID userId, Instant dueAfterExclusive) {
        return jdbcClient.sql("""
            select id, user_id, note_id, task_source, task_type, title, description, status,
                   priority, due_at, related_entity_type, related_entity_id, created_at, updated_at
            from tasks
            where user_id = :userId
              and status in ('TODO', 'IN_PROGRESS')
              and due_at is not null
              and due_at > :dueAfterExclusive
            order by due_at asc,
                     case when task_source = 'SYSTEM' then 0 else 1 end,
                     priority desc,
                     created_at asc
            """)
            .param("userId", userId)
            .param("dueAfterExclusive", Timestamp.from(dueAfterExclusive))
            .query((rs, rowNum) -> mapView(rs))
            .list();
    }

    @Override
    public Optional<TaskApplicationService.TaskView> findByIdAndUserId(UUID taskId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, task_source, task_type, title, description, status,
                   priority, due_at, related_entity_type, related_entity_id, created_at, updated_at
            from tasks
            where id = :taskId and user_id = :userId
            """)
            .param("taskId", taskId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public Optional<TaskApplicationService.TaskView> findOpenByUserIdAndSourceAndTaskTypeAndNoteId(UUID userId,
                                                                                                    TaskSource taskSource,
                                                                                                    String taskType,
                                                                                                    UUID noteId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, task_source, task_type, title, description, status,
                   priority, due_at, related_entity_type, related_entity_id, created_at, updated_at
            from tasks
            where user_id = :userId
              and task_source = :taskSource
              and task_type = :taskType
              and note_id = :noteId
              and status in ('TODO', 'IN_PROGRESS')
            order by updated_at desc
            limit 1
            """)
            .param("userId", userId)
            .param("taskSource", taskSource.name())
            .param("taskType", taskType)
            .param("noteId", noteId)
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public void updateStatus(UUID taskId, TaskStatus status) {
        jdbcClient.sql("""
            update tasks
            set status = :status,
                updated_at = current_timestamp
            where id = :taskId
            """)
            .param("status", status.name())
            .param("taskId", taskId)
            .update();
    }

    @Override
    public void refreshOpenTask(UUID taskId,
                                String title,
                                String description,
                                int priority,
                                Instant dueAt,
                                TaskRelatedEntityType relatedEntityType,
                                UUID relatedEntityId) {
        jdbcClient.sql("""
            update tasks
            set title = :title,
                description = :description,
                priority = :priority,
                due_at = :dueAt,
                related_entity_type = :relatedEntityType,
                related_entity_id = :relatedEntityId,
                updated_at = current_timestamp
            where id = :taskId
            """)
            .param("title", title)
            .param("description", description)
            .param("priority", priority)
            .param("dueAt", timestampOrNull(dueAt))
            .param("relatedEntityType", relatedEntityType.name())
            .param("relatedEntityId", relatedEntityId)
            .param("taskId", taskId)
            .update();
    }

    private TaskApplicationService.TaskView mapView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskApplicationService.TaskView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("note_id", UUID.class),
            TaskSource.valueOf(rs.getString("task_source")),
            rs.getString("task_type"),
            rs.getString("title"),
            rs.getString("description"),
            TaskStatus.valueOf(rs.getString("status")),
            rs.getInt("priority"),
            timestampToInstant(rs.getTimestamp("due_at")),
            TaskRelatedEntityType.valueOf(rs.getString("related_entity_type")),
            rs.getObject("related_entity_id", UUID.class),
            timestampToInstant(rs.getTimestamp("created_at")),
            timestampToInstant(rs.getTimestamp("updated_at"))
        );
    }

    private Timestamp timestampOrNull(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
