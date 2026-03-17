package com.noteops.agent.persistence.review;

import com.noteops.agent.application.review.ReviewApplicationService;
import com.noteops.agent.domain.review.ReviewCompletionReason;
import com.noteops.agent.domain.review.ReviewCompletionStatus;
import com.noteops.agent.domain.review.ReviewQueueType;
import com.noteops.agent.domain.review.ReviewSelfRecallResult;
import com.noteops.agent.persistence.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcReviewStateRepository implements ReviewStateRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcReviewStateRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public void createInitialScheduleIfMissing(UUID userId, UUID noteId, Instant now) {
        jdbcClient.sql("""
            insert into review_states (
                id, user_id, note_id, queue_type, mastery_score, next_review_at,
                completion_status, unfinished_count, retry_after_hours, review_meta
            )
            select :id, :userId, :noteId, :queueType, :masteryScore, :nextReviewAt,
                   :completionStatus, :unfinishedCount, :retryAfterHours, cast(:reviewMeta as jsonb)
            where not exists (
                select 1 from review_states
                where user_id = :userId and note_id = :noteId and queue_type = :queueType
            )
            """)
            .param("id", UUID.randomUUID())
            .param("userId", userId)
            .param("noteId", noteId)
            .param("queueType", ReviewQueueType.SCHEDULE.name())
            .param("masteryScore", BigDecimal.ZERO)
            .param("nextReviewAt", Timestamp.from(now))
            .param("completionStatus", ReviewCompletionStatus.NOT_STARTED.name())
            .param("unfinishedCount", 0)
            .param("retryAfterHours", 0)
            .param("reviewMeta", jsonSupport.write(Map.of()))
            .update();
    }

    @Override
    public List<ReviewApplicationService.ReviewStateView> findDueByUserId(UUID userId, Instant now) {
        return jdbcClient.sql("""
            select id, user_id, note_id, queue_type, mastery_score, last_reviewed_at, next_review_at,
                   completion_status, completion_reason, unfinished_count, retry_after_hours, review_meta, created_at, updated_at
            from review_states
            where user_id = :userId and next_review_at <= :now
            order by case when queue_type = 'RECALL' then 0 else 1 end,
                     next_review_at asc,
                     created_at asc
            """)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .query((rs, rowNum) -> mapView(rs))
            .list();
    }

    @Override
    public Optional<ReviewApplicationService.ReviewStateView> findByIdAndUserId(UUID reviewStateId, UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, note_id, queue_type, mastery_score, last_reviewed_at, next_review_at,
                   completion_status, completion_reason, unfinished_count, retry_after_hours, review_meta, created_at, updated_at
            from review_states
            where id = :reviewStateId and user_id = :userId
            """)
            .param("reviewStateId", reviewStateId)
            .param("userId", userId)
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public Optional<ReviewApplicationService.ReviewStateView> findByUserIdAndNoteIdAndQueueType(UUID userId, UUID noteId, ReviewQueueType queueType) {
        return jdbcClient.sql("""
            select id, user_id, note_id, queue_type, mastery_score, last_reviewed_at, next_review_at,
                   completion_status, completion_reason, unfinished_count, retry_after_hours, review_meta, created_at, updated_at
            from review_states
            where user_id = :userId and note_id = :noteId and queue_type = :queueType
            """)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("queueType", queueType.name())
            .query((rs, rowNum) -> mapView(rs))
            .optional();
    }

    @Override
    public ReviewApplicationService.ReviewStateView create(UUID userId,
                                                           UUID noteId,
                                                           ReviewQueueType queueType,
                                                           ReviewCompletionStatus completionStatus,
                                                           ReviewCompletionReason completionReason,
                                                           ReviewSelfRecallResult selfRecallResult,
                                                           String note,
                                                           BigDecimal masteryScore,
                                                           Instant lastReviewedAt,
                                                           Instant nextReviewAt,
                                                           int unfinishedCount,
                                                           int retryAfterHours) {
        UUID reviewStateId = UUID.randomUUID();
        jdbcClient.sql("""
            insert into review_states (
                id, user_id, note_id, queue_type, mastery_score, last_reviewed_at, next_review_at,
                completion_status, completion_reason, unfinished_count, retry_after_hours, review_meta
            ) values (
                :id, :userId, :noteId, :queueType, :masteryScore, :lastReviewedAt, :nextReviewAt,
                :completionStatus, :completionReason, :unfinishedCount, :retryAfterHours, cast(:reviewMeta as jsonb)
            )
            """)
            .param("id", reviewStateId)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("queueType", queueType.name())
            .param("masteryScore", masteryScore)
            .param("lastReviewedAt", timestampOrNull(lastReviewedAt))
            .param("nextReviewAt", timestampOrNull(nextReviewAt))
            .param("completionStatus", completionStatus.name())
            .param("completionReason", completionReason == null ? null : completionReason.name())
            .param("unfinishedCount", unfinishedCount)
            .param("retryAfterHours", retryAfterHours)
            .param("reviewMeta", jsonSupport.write(reviewMeta(selfRecallResult, note)))
            .update();
        return findByIdAndUserId(reviewStateId, userId).orElseThrow();
    }

    @Override
    public void update(UUID reviewStateId,
                       ReviewCompletionStatus completionStatus,
                       ReviewCompletionReason completionReason,
                       ReviewSelfRecallResult selfRecallResult,
                       String note,
                       BigDecimal masteryScore,
                       Instant lastReviewedAt,
                       Instant nextReviewAt,
                       int unfinishedCount,
                       int retryAfterHours) {
        jdbcClient.sql("""
            update review_states
            set completion_status = :completionStatus,
                completion_reason = :completionReason,
                mastery_score = :masteryScore,
                last_reviewed_at = :lastReviewedAt,
                next_review_at = :nextReviewAt,
                unfinished_count = :unfinishedCount,
                retry_after_hours = :retryAfterHours,
                review_meta = cast(:reviewMeta as jsonb),
                updated_at = current_timestamp
            where id = :reviewStateId
            """)
            .param("completionStatus", completionStatus.name())
            .param("completionReason", completionReason == null ? null : completionReason.name())
            .param("masteryScore", masteryScore)
            .param("lastReviewedAt", timestampOrNull(lastReviewedAt))
            .param("nextReviewAt", timestampOrNull(nextReviewAt))
            .param("unfinishedCount", unfinishedCount)
            .param("retryAfterHours", retryAfterHours)
            .param("reviewMeta", jsonSupport.write(reviewMeta(selfRecallResult, note)))
            .param("reviewStateId", reviewStateId)
            .update();
    }

    private ReviewApplicationService.ReviewStateView mapView(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> reviewMeta = jsonSupport.readMap(rs.getString("review_meta"));
        return new ReviewApplicationService.ReviewStateView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("note_id", UUID.class),
            ReviewQueueType.valueOf(rs.getString("queue_type")),
            rs.getBigDecimal("mastery_score"),
            timestampToInstant(rs.getTimestamp("last_reviewed_at")),
            timestampToInstant(rs.getTimestamp("next_review_at")),
            ReviewCompletionStatus.valueOf(rs.getString("completion_status")),
            nullableReason(rs.getString("completion_reason")),
            nullableSelfRecallResult(reviewMeta.get("self_recall_result")),
            nullableText(reviewMeta.get("note")),
            rs.getInt("unfinished_count"),
            rs.getInt("retry_after_hours"),
            timestampToInstant(rs.getTimestamp("created_at")),
            timestampToInstant(rs.getTimestamp("updated_at"))
        );
    }

    private ReviewCompletionReason nullableReason(String rawValue) {
        return rawValue == null ? null : ReviewCompletionReason.valueOf(rawValue);
    }

    private ReviewSelfRecallResult nullableSelfRecallResult(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return ReviewSelfRecallResult.valueOf(String.valueOf(rawValue));
    }

    private String nullableText(Object rawValue) {
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    private Map<String, Object> reviewMeta(ReviewSelfRecallResult selfRecallResult, String note) {
        if (selfRecallResult == null && note == null) {
            return Map.of();
        }
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        if (selfRecallResult != null) {
            meta.put("self_recall_result", selfRecallResult.name());
        }
        if (note != null) {
            meta.put("note", note);
        }
        return meta;
    }

    private Timestamp timestampOrNull(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
