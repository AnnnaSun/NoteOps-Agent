package com.noteops.agent.persistence.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-search-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcSearchRepositoryIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SearchRepository searchRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists note_contents").update();
        jdbcClient.sql("drop table if exists notes").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(4096)").update();
        jdbcClient.sql("""
            create table notes (
                id uuid primary key,
                user_id uuid not null,
                title varchar(255) not null,
                current_summary text,
                current_key_points jsonb not null default '[]',
                latest_content_id uuid,
                updated_at timestamp with time zone not null default current_timestamp
            )
            """).update();
        jdbcClient.sql("""
            create table note_contents (
                id uuid primary key,
                user_id uuid not null,
                note_id uuid not null,
                content_type varchar(32) not null,
                raw_text text,
                clean_text text,
                created_at timestamp with time zone not null default current_timestamp
            )
            """).update();
    }

    @Test
    void findsNotesForUserAndMapsLatestContent() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        UUID latestContentOne = UUID.randomUUID();
        UUID latestContentTwo = UUID.randomUUID();
        UUID otherLatestContent = UUID.randomUUID();

        insertContent(latestContentOne, userId, UUID.randomUUID(), "one raw", "one clean");
        insertContent(latestContentTwo, userId, UUID.randomUUID(), "two raw", "two clean");
        insertContent(otherLatestContent, otherUserId, UUID.randomUUID(), "other raw", "other clean");

        UUID newerNoteId = UUID.randomUUID();
        UUID olderNoteId = UUID.randomUUID();
        UUID otherUserNoteId = UUID.randomUUID();

        insertNote(newerNoteId, userId, "Newer note", "newer summary", List.of("newer point"), latestContentTwo, Instant.parse("2026-03-16T03:00:00Z"));
        insertNote(olderNoteId, userId, "Older note", "older summary", List.of("older point"), latestContentOne, Instant.parse("2026-03-16T02:00:00Z"));
        insertNote(otherUserNoteId, otherUserId, "Other note", "other summary", List.of("other point"), otherLatestContent, Instant.parse("2026-03-16T04:00:00Z"));

        List<SearchRepository.SearchCandidate> candidates = searchRepository.findByUserId(userId);

        assertThat(candidates).extracting(SearchRepository.SearchCandidate::noteId)
            .containsExactly(newerNoteId, olderNoteId);
        assertThat(candidates.getFirst().latestContent()).isEqualTo("two clean");
        assertThat(candidates.getLast().latestContent()).isEqualTo("one clean");
    }

    private void insertContent(UUID id, UUID userId, UUID noteId, String rawText, String cleanText) {
        jdbcClient.sql("""
            insert into note_contents (id, user_id, note_id, content_type, raw_text, clean_text)
            values (:id, :userId, :noteId, 'CAPTURE_RAW', :rawText, :cleanText)
            """)
            .param("id", id)
            .param("userId", userId)
            .param("noteId", noteId)
            .param("rawText", rawText)
            .param("cleanText", cleanText)
            .update();
    }

    private void insertNote(UUID id, UUID userId, String title, String currentSummary, List<String> currentKeyPoints,
                            UUID latestContentId, Instant updatedAt) {
        jdbcClient.sql("""
            insert into notes (id, user_id, title, current_summary, current_key_points, latest_content_id, updated_at)
            values (:id, :userId, :title, :currentSummary, cast(:currentKeyPoints as jsonb), :latestContentId, :updatedAt)
            """)
            .param("id", id)
            .param("userId", userId)
            .param("title", title)
            .param("currentSummary", currentSummary)
            .param("currentKeyPoints", "[\"" + String.join("\",\"", currentKeyPoints) + "\"]")
            .param("latestContentId", latestContentId)
            .param("updatedAt", updatedAt)
            .update();
    }
}
