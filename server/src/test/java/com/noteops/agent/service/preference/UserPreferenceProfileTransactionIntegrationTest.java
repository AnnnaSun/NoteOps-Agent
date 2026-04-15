package com.noteops.agent.service.preference;

import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-preference-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class UserPreferenceProfileTransactionIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UserPreferenceProfileApplicationService userPreferenceProfileApplicationService;

    @Autowired
    private FailingUserPreferenceProfileRepository failingUserPreferenceProfileRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists agent_traces").update();
        jdbcClient.sql("drop domain if exists timestamptz").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("create domain timestamptz as timestamp with time zone").update();
        jdbcClient.sql("""
            create table agent_traces (
                id uuid primary key,
                user_id uuid not null,
                entry_type varchar(64) not null,
                goal text,
                root_entity_type varchar(64),
                root_entity_id uuid,
                status varchar(32) not null,
                orchestrator_state jsonb not null,
                worker_sequence jsonb not null,
                result_summary text,
                started_at timestamptz,
                ended_at timestamptz,
                created_at timestamptz not null default current_timestamp
            )
            """).update();
        failingUserPreferenceProfileRepository.failOnUpsert = true;
    }

    @Test
    void preservesFailedTraceWhenProfilePersistenceFails() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userPreferenceProfileApplicationService.upsert(
            new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                userId.toString(),
                new InterestProfile(
                    List.of("agents"),
                    List.of(),
                    Map.of("HN", 0.8),
                    Map.of(),
                    Map.of()
                )
            )
        ))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated preference profile persistence failure");

        Integer traceCount = jdbcClient.sql("""
            select count(*)
            from agent_traces
            where user_id = :userId and entry_type = 'USER_PREFERENCE_PROFILE_UPSERT'
            """)
            .param("userId", userId)
            .query(Integer.class)
            .single();

        assertThat(traceCount).isEqualTo(1);
        assertThat(jdbcClient.sql("""
            select status
            from agent_traces
            where user_id = :userId and entry_type = 'USER_PREFERENCE_PROFILE_UPSERT'
            """)
            .param("userId", userId)
            .query(String.class)
            .single())
            .isEqualTo("FAILED");
    }

    @TestConfiguration
    static class TransactionTestConfig {

        @Bean
        @Primary
        FailingUserPreferenceProfileRepository failingUserPreferenceProfileRepository() {
            return new FailingUserPreferenceProfileRepository();
        }
    }

    static final class FailingUserPreferenceProfileRepository implements UserPreferenceProfileRepository {

        private boolean failOnUpsert = true;

        @Override
        public java.util.Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
            return java.util.Optional.empty();
        }

        @Override
        public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
            if (failOnUpsert) {
                throw new RuntimeException("simulated preference profile persistence failure");
            }
            throw new UnsupportedOperationException("not needed in this test");
        }
    }
}
