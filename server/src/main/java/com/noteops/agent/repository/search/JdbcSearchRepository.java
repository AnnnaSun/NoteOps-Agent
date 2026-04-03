package com.noteops.agent.repository.search;

import com.noteops.agent.common.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSearchRepository implements SearchRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcSearchRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public List<SearchCandidate> findByUserId(UUID userId) {
        return jdbcClient.sql("""
            select n.id,
                   n.user_id,
                   n.title,
                   n.current_summary,
                   n.current_key_points,
                   n.current_tags,
                   nc.source_uri,
                   nc.content_type as latest_content_type,
                   coalesce(nc.clean_text, nc.raw_text, '') as latest_content,
                   n.updated_at,
                   rs.next_review_at,
                   rs.mastery_score
            from notes n
            left join note_contents nc on nc.id = n.latest_content_id
            left join review_states rs
              on rs.user_id = n.user_id
             and rs.note_id = n.id
             and rs.queue_type = 'SCHEDULE'
            where n.user_id = :userId
            order by n.updated_at desc, n.id asc
            """)
            .param("userId", userId)
            .query((rs, rowNum) -> new SearchCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                rs.getString("current_summary"),
                jsonSupport.readStringList(rs.getString("current_key_points")),
                jsonSupport.readStringList(rs.getString("current_tags")),
                rs.getString("source_uri"),
                rs.getString("latest_content_type"),
                blankToNull(rs.getString("latest_content")),
                rs.getTimestamp("updated_at").toInstant(),
                timestampToInstant(rs.getTimestamp("next_review_at")),
                rs.getBigDecimal("mastery_score")
            ))
            .list();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private Instant timestampToInstant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
