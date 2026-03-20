package com.noteops.agent.repository.review;

import com.noteops.agent.service.review.ReviewApplicationService;
import com.noteops.agent.model.review.ReviewCompletionReason;
import com.noteops.agent.model.review.ReviewCompletionStatus;
import com.noteops.agent.model.review.ReviewQueueType;
import com.noteops.agent.model.review.ReviewSelfRecallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-review-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcReviewStateRepositoryIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ReviewStateRepository reviewStateRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists review_states").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(4096)").update();
        jdbcClient.sql("""
            create table review_states (
                id uuid primary key,
                user_id uuid not null,
                note_id uuid not null,
                queue_type varchar(16) not null,
                mastery_score numeric(5, 2) not null default 0,
                last_reviewed_at timestamp with time zone,
                next_review_at timestamp with time zone,
                completion_status varchar(16) not null,
                completion_reason varchar(16),
                unfinished_count integer not null default 0,
                retry_after_hours integer not null default 0,
                review_meta jsonb not null default '{}',
                created_at timestamp with time zone not null default current_timestamp,
                updated_at timestamp with time zone not null default current_timestamp,
                constraint uq_review_states_user_note_queue unique (user_id, note_id, queue_type)
            )
            """).update();
    }

    @Test
    void createInitialScheduleIfMissingIsConflictSafe() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        reviewStateRepository.createInitialScheduleIfMissing(userId, noteId, Instant.parse("2026-03-18T01:00:00Z"));
        reviewStateRepository.createInitialScheduleIfMissing(userId, noteId, Instant.parse("2026-03-18T02:00:00Z"));

        Integer count = jdbcClient.sql("""
            select count(*)
            from review_states
            where user_id = :userId and note_id = :noteId and queue_type = :queueType
            """)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("queueType", ReviewQueueType.SCHEDULE.name())
            .query(Integer.class)
            .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsRecallStateWithReviewMetaFeedback() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        ReviewApplicationService.ReviewStateView created = reviewStateRepository.create(
            userId,
            noteId,
            ReviewQueueType.RECALL,
            ReviewCompletionStatus.PARTIAL,
            ReviewCompletionReason.TIME_LIMIT,
            ReviewSelfRecallResult.GOOD,
            "Strong recall",
            BigDecimal.valueOf(42),
            Instant.parse("2026-03-16T01:00:00Z"),
            Instant.parse("2026-03-17T01:00:00Z"),
            1,
            24
        );

        assertThat(created.selfRecallResult()).isEqualTo(ReviewSelfRecallResult.GOOD);
        assertThat(created.note()).isEqualTo("Strong recall");

        String rawReviewMeta = jdbcClient.sql("select review_meta from review_states where id = :id")
            .param("id", created.id())
            .query(String.class)
            .single();

        assertThat(rawReviewMeta).contains("\"self_recall_result\":\"GOOD\"");
        assertThat(rawReviewMeta).contains("\"note\":\"Strong recall\"");
    }

    @Test
    void updatesRecallStateAndMapsReviewMetaBack() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        ReviewApplicationService.ReviewStateView created = reviewStateRepository.create(
            userId,
            noteId,
            ReviewQueueType.RECALL,
            ReviewCompletionStatus.NOT_STARTED,
            ReviewCompletionReason.DEFERRED,
            null,
            null,
            BigDecimal.valueOf(18),
            Instant.parse("2026-03-16T01:00:00Z"),
            Instant.parse("2026-03-17T01:00:00Z"),
            0,
            4
        );

        reviewStateRepository.update(
            created.id(),
            ReviewCompletionStatus.COMPLETED,
            null,
            ReviewSelfRecallResult.VAGUE,
            null,
            BigDecimal.valueOf(28),
            Instant.parse("2026-03-18T01:00:00Z"),
            Instant.parse("2026-03-25T01:00:00Z"),
            0,
            0
        );

        ReviewApplicationService.ReviewStateView updated = reviewStateRepository.findByIdAndUserId(created.id(), userId).orElseThrow();

        assertThat(updated.selfRecallResult()).isEqualTo(ReviewSelfRecallResult.VAGUE);
        assertThat(updated.note()).isNull();

        String rawReviewMeta = jdbcClient.sql("select review_meta from review_states where id = :id")
            .param("id", created.id())
            .query(String.class)
            .single();

        assertThat(rawReviewMeta).contains("\"self_recall_result\":\"VAGUE\"");
        assertThat(rawReviewMeta).doesNotContain("\"note\"");
    }
}
