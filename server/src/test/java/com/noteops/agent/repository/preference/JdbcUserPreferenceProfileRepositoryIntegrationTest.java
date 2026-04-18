package com.noteops.agent.repository.preference;

import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.preference.InterestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-preference-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcUserPreferenceProfileRepositoryIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserPreferenceProfileRepository userPreferenceProfileRepository;

    @Autowired
    private JsonSupport jsonSupport;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUpSchema() throws Exception {
        jdbcClient.sql("drop table if exists user_preference_profiles").update();
        jdbcClient.sql("drop domain if exists timestamptz").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("create domain timestamptz as timestamp with time zone").update();
        ScriptUtils.executeSqlScript(
            dataSource.getConnection(),
            new EncodedResource(new ClassPathResource("db/migration/V6__create_user_preference_profiles_table.sql"), StandardCharsets.UTF_8)
        );
    }

    @Test
    void createsAndLoadsStructuredInterestProfile() {
        UUID userId = UUID.randomUUID();
        InterestProfile interestProfile = new InterestProfile(
            List.of("agents", "tooling"),
            List.of("crypto"),
            Map.of("HN", 0.8, "GITHUB", 1.0),
            Map.of("save_as_note", 0.7),
            Map.of("review", 0.9)
        );

        UserPreferenceProfileRepository.UserPreferenceProfileRecord created =
            userPreferenceProfileRepository.upsert(userId, interestProfile);

        assertThat(created.id()).isNotNull();
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.interestProfile()).isEqualTo(interestProfile);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        UserPreferenceProfileRepository.UserPreferenceProfileRecord loaded =
            userPreferenceProfileRepository.findByUserId(userId).orElseThrow();

        assertThat(loaded).isEqualTo(created);
        assertThat(jsonSupport.write(loaded.interestProfile().toMap())).contains("preferred_topics");
    }

    @Test
    void updatesExistingProfileForSameUser() {
        UUID userId = UUID.randomUUID();
        UserPreferenceProfileRepository.UserPreferenceProfileRecord created =
            userPreferenceProfileRepository.upsert(userId, new InterestProfile(
                List.of("agents"),
                List.of(),
                Map.of("HN", 0.8),
                Map.of(),
                Map.of()
            ));

        UserPreferenceProfileRepository.UserPreferenceProfileRecord updated =
            userPreferenceProfileRepository.upsert(userId, new InterestProfile(
                List.of("search"),
                List.of("crypto"),
                Map.of("GITHUB", 1.0),
                Map.of("promote_to_idea", 0.6),
                Map.of("idea_followup", 0.8)
            ));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.interestProfile().preferredTopics()).containsExactly("search");
        assertThat(updated.interestProfile().ignoredTopics()).containsExactly("crypto");
        assertThat(updated.interestProfile().actionBias()).containsEntry("promote_to_idea", 0.6);
    }

    @Test
    void keepsSingleRowWhenTwoTransactionsUpsertSameUserConcurrently() throws Exception {
        UUID userId = UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        try {
            List<Future<UserPreferenceProfileRepository.UserPreferenceProfileRecord>> futures = new ArrayList<>();
            futures.add(executorService.submit(() -> transactionTemplate.execute(status -> {
                awaitBarrier(barrier);
                return userPreferenceProfileRepository.upsert(userId, new InterestProfile(
                    List.of("agents"),
                    List.of(),
                    Map.of("HN", 0.8),
                    Map.of(),
                    Map.of()
                ));
            })));
            futures.add(executorService.submit(() -> transactionTemplate.execute(status -> {
                awaitBarrier(barrier);
                return userPreferenceProfileRepository.upsert(userId, new InterestProfile(
                    List.of("search"),
                    List.of("crypto"),
                    Map.of("GITHUB", 1.0),
                    Map.of("promote_to_idea", 0.6),
                    Map.of()
                ));
            })));

            List<UserPreferenceProfileRepository.UserPreferenceProfileRecord> results = new ArrayList<>();
            for (Future<UserPreferenceProfileRepository.UserPreferenceProfileRecord> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).hasSize(2);
            assertThat(jdbcClient.sql("""
                select count(*)
                from user_preference_profiles
                where user_id = :userId
                """)
                .param("userId", userId)
                .query(Integer.class)
                .single())
                .isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
