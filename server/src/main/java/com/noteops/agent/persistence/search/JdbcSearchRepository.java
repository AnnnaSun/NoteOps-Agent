package com.noteops.agent.persistence.search;

import com.noteops.agent.persistence.JsonSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
                   coalesce(nc.clean_text, nc.raw_text, '') as latest_content,
                   n.updated_at
            from notes n
            left join note_contents nc on nc.id = n.latest_content_id
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
                blankToNull(rs.getString("latest_content")),
                rs.getTimestamp("updated_at").toInstant()
            ))
            .list();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
