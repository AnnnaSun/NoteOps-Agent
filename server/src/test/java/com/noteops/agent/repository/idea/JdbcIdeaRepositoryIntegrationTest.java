package com.noteops.agent.repository.idea;

import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-idea-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcIdeaRepositoryIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private IdeaRepository ideaRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists ideas").update();
        jdbcClient.sql("drop table if exists notes").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("""
            create table notes (
                id uuid primary key,
                user_id uuid not null,
                note_type varchar(64) not null,
                status varchar(32) not null,
                title varchar(255) not null,
                current_summary text,
                current_key_points jsonb not null default '[]',
                current_tags jsonb not null default '[]',
                current_topic_labels jsonb not null default '[]',
                current_relation_summary jsonb not null default '{}',
                importance_score numeric(5, 2) not null default 0,
                latest_content_id uuid,
                version integer not null default 1,
                extra_attributes jsonb not null default '{}',
                created_at timestamp with time zone not null default current_timestamp,
                updated_at timestamp with time zone not null default current_timestamp
            )
            """).update();
        jdbcClient.sql("""
            create table ideas (
                id uuid primary key,
                user_id uuid not null,
                source_mode varchar(32) not null,
                source_note_id uuid,
                title varchar(255) not null,
                raw_description text,
                status varchar(32) not null,
                assessment_result jsonb not null default '{}',
                created_at timestamp with time zone not null default current_timestamp,
                updated_at timestamp with time zone not null default current_timestamp,
                constraint fk_ideas_source_note_id foreign key (source_note_id) references notes (id),
                constraint chk_ideas_source_mode check (source_mode in ('FROM_NOTE', 'INDEPENDENT')),
                constraint chk_ideas_status check (status in ('CAPTURED', 'ASSESSED', 'PLANNED', 'IN_PROGRESS', 'ARCHIVED')),
                constraint chk_ideas_source_binding check (
                    (source_mode = 'FROM_NOTE' and source_note_id is not null)
                    or (source_mode = 'INDEPENDENT' and source_note_id is null)
                )
            )
            """).update();
    }

    @Test
    void createsAndLoadsIdeaFromNote() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        insertNote(noteId, userId, "Source note");

        IdeaAssessmentResult assessmentResult = new IdeaAssessmentResult(
            "Problem statement",
            "Busy solo founder",
            "Users will pay for structured execution",
            List.of("Landing page", "Interview 5 users"),
            List.of("Write outline", "Call 3 users"),
            List.of("Low willingness to pay"),
            "Need a tighter target segment"
        );

        IdeaRepository.IdeaRecord created = ideaRepository.create(
            userId,
            IdeaSourceMode.FROM_NOTE,
            noteId,
            "Idea from note",
            "Turn this note into an idea",
            IdeaStatus.CAPTURED,
            assessmentResult
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.sourceMode()).isEqualTo(IdeaSourceMode.FROM_NOTE);
        assertThat(created.sourceNoteId()).isEqualTo(noteId);
        assertThat(created.title()).isEqualTo("Idea from note");
        assertThat(created.rawDescription()).isEqualTo("Turn this note into an idea");
        assertThat(created.status()).isEqualTo(IdeaStatus.CAPTURED);
        assertThat(created.assessmentResult().problemStatement()).isEqualTo("Problem statement");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        IdeaRepository.IdeaRecord loaded = ideaRepository.findByIdAndUserId(created.id(), userId).orElseThrow();

        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void createsIndependentIdeaWithEmptyAssessmentResult() {
        UUID userId = UUID.randomUUID();

        IdeaRepository.IdeaRecord created = ideaRepository.create(
            userId,
            IdeaSourceMode.INDEPENDENT,
            null,
            "Independent idea",
            null,
            IdeaStatus.CAPTURED,
            null
        );

        assertThat(created.sourceMode()).isEqualTo(IdeaSourceMode.INDEPENDENT);
        assertThat(created.sourceNoteId()).isNull();
        assertThat(created.assessmentResult()).isEqualTo(IdeaAssessmentResult.empty());
        assertThat(created.rawDescription()).isNull();
    }

    private void insertNote(UUID noteId, UUID userId, String title) {
        jdbcClient.sql("""
            insert into notes (
                id, user_id, note_type, status, title, current_summary, current_key_points, current_tags,
                current_topic_labels, current_relation_summary, importance_score, latest_content_id, version, extra_attributes
            ) values (
                :id, :userId, 'CAPTURE_NOTE', 'ACTIVE', :title, null, cast(:currentKeyPoints as jsonb),
                cast(:currentTags as jsonb), cast(:currentTopicLabels as jsonb), cast(:currentRelationSummary as jsonb),
                :importanceScore, null, 1, cast(:extraAttributes as jsonb)
            )
            """)
            .param("id", noteId)
            .param("userId", userId)
            .param("title", title)
            .param("currentKeyPoints", "[]")
            .param("currentTags", "[]")
            .param("currentTopicLabels", "[]")
            .param("currentRelationSummary", "{}")
            .param("importanceScore", 50)
            .param("extraAttributes", "{}")
            .update();
    }
}
