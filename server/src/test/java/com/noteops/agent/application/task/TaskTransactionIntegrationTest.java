package com.noteops.agent.application.task;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-task-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class TaskTransactionIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TaskApplicationService taskApplicationService;

    @MockitoBean
    private NoteRepository noteRepository;

    @MockitoBean
    private AgentTraceRepository agentTraceRepository;

    @Autowired
    private FailingUserActionEventRepository failingUserActionEventRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists tasks").update();
        jdbcClient.sql("""
            create table tasks (
                id uuid primary key,
                user_id uuid not null,
                note_id uuid,
                task_source varchar(16) not null,
                task_type varchar(64) not null,
                title varchar(255) not null,
                description text,
                status varchar(16) not null,
                priority integer not null default 0,
                due_at timestamp with time zone,
                related_entity_type varchar(64),
                related_entity_id uuid,
                extra_attributes varchar(2048) not null default '{}',
                created_at timestamp with time zone not null default current_timestamp,
                updated_at timestamp with time zone not null default current_timestamp
            )
            """).update();

        failingUserActionEventRepository.fail = true;
        when(agentTraceRepository.create(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID());
    }

    @Test
    void rollsBackTaskInsertWhenEventAppendFails() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> taskApplicationService.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Transactional task",
                "Should roll back",
                null,
                1,
                null,
                null,
                null,
                null
            )
        ))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated user action event failure");

        Integer taskCount = jdbcClient.sql("select count(*) from tasks where user_id = :userId")
            .param("userId", userId)
            .query(Integer.class)
            .single();

        assertThat(taskCount).isZero();
    }

    @Test
    void rejectsDuplicateOpenUserTaskWithoutWritingSecondRow() {
        UUID userId = UUID.randomUUID();
        failingUserActionEventRepository.fail = false;

        taskApplicationService.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Transactional task",
                "Should stay unique",
                "GENERAL",
                1,
                null,
                null,
                null,
                null
            )
        );

        assertThatThrownBy(() -> taskApplicationService.create(
            new TaskApplicationService.CreateTaskCommand(
                userId.toString(),
                "Transactional task",
                "Should stay unique",
                "GENERAL",
                1,
                null,
                null,
                null,
                null
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("an open user task with the same title and binding already exists");

        Integer taskCount = jdbcClient.sql("select count(*) from tasks where user_id = :userId")
            .param("userId", userId)
            .query(Integer.class)
            .single();

        assertThat(taskCount).isEqualTo(1);
    }

    @TestConfiguration
    static class TransactionTestConfig {

        @Bean
        @Primary
        FailingUserActionEventRepository failingUserActionEventRepository() {
            return new FailingUserActionEventRepository();
        }
    }

    static class FailingUserActionEventRepository implements UserActionEventRepository {

        private boolean fail;

        @Override
        public void append(UUID userId,
                           String eventType,
                           String entityType,
                           UUID entityId,
                           UUID traceId,
                           Map<String, Object> payload) {
            if (fail) {
                throw new RuntimeException("simulated user action event failure");
            }
        }
    }
}
