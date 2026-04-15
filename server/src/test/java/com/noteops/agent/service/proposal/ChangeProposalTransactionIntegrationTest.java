package com.noteops.agent.service.proposal;

import com.noteops.agent.service.note.NoteQueryService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.proposal.ChangeProposalRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-proposal-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class ChangeProposalTransactionIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ChangeProposalApplicationService changeProposalApplicationService;

    @Autowired
    private TestNoteRepository noteRepository;

    @Autowired
    private TestChangeProposalRepository changeProposalRepository;

    @Autowired
    private FailingToolInvocationLogRepository failingToolInvocationLogRepository;

    @MockitoBean
    private AgentTraceRepository agentTraceRepository;

    @MockitoBean
    private UserActionEventRepository userActionEventRepository;

    private UUID userId;
    private UUID noteId;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists change_proposals").update();
        jdbcClient.sql("drop table if exists notes").update();
        jdbcClient.sql("""
            create table notes (
                id uuid primary key,
                user_id uuid not null,
                title varchar(255) not null,
                current_summary text,
                current_key_points varchar(2048) not null,
                latest_content_id uuid,
                latest_content_type varchar(32),
                source_uri text,
                raw_text text,
                clean_text text,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """).update();
        jdbcClient.sql("""
            create table change_proposals (
                id uuid primary key,
                user_id uuid not null,
                note_id uuid not null,
                trace_id uuid,
                proposal_type varchar(64) not null,
                target_layer varchar(16) not null,
                risk_level varchar(8) not null,
                diff_summary text not null,
                before_summary text,
                before_key_points varchar(2048) not null,
                after_summary text,
                after_key_points varchar(2048) not null,
                rollback_token varchar(128),
                status varchar(16) not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """).update();

        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        noteRepository.insert(
            noteId,
            userId,
            "Kickoff",
            "Kickoff covered timeline. We aligned owners. Risks were called out.",
            List.of("Kickoff covered timeline.", "We aligned owners.", "Risks were called out."),
            "Kickoff covered timeline. We aligned owners. Risks were called out."
        );

        failingToolInvocationLogRepository.fail = false;
        when(agentTraceRepository.create(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID());
    }

    @Test
    void rollsBackApplyWhenToolLogWriteFails() {
        ChangeProposalApplicationService.ChangeProposalView proposal = changeProposalRepository.create(
            userId,
            noteId,
            UUID.randomUUID(),
            "REFRESH_INTERPRETATION",
            ChangeProposalTargetLayer.INTERPRETATION,
            ChangeProposalRiskLevel.LOW,
            "Refresh current_summary and current_key_points from the latest note content.",
            Map.of(
                "current_summary", "Kickoff covered timeline. We aligned owners. Risks were called out.",
                "current_key_points", List.of("Kickoff covered timeline.", "We aligned owners.", "Risks were called out.")
            ),
            Map.of(
                "current_summary", "Kickoff. Key points: Kickoff covered timeline. We aligned owners. Risks were called out.",
                "current_key_points", List.of("Kickoff covered timeline.", "We aligned owners.", "Risks were called out.")
            ),
            List.of(Map.of("content_type", "CAPTURE_RAW"))
        );

        failingToolInvocationLogRepository.fail = true;

        assertThatThrownBy(() -> changeProposalApplicationService.apply(noteId.toString(), proposal.id().toString(), userId.toString()))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated tool invocation failure");

        NoteQueryService.NoteDetailView note = noteRepository.findByIdAndUserId(noteId, userId).orElseThrow();
        assertThat(note.currentSummary()).isEqualTo("Kickoff covered timeline. We aligned owners. Risks were called out.");
        assertThat(changeProposalRepository.findByIdAndUserId(proposal.id(), userId).orElseThrow())
            .extracting(ChangeProposalApplicationService.ChangeProposalView::status)
            .isEqualTo(ChangeProposalStatus.PENDING_REVIEW);
    }

    @Test
    void rollsBackRejectWhenUserActionEventWriteFails() {
        ChangeProposalApplicationService.ChangeProposalView proposal = changeProposalRepository.create(
            userId,
            noteId,
            UUID.randomUUID(),
            "REFRESH_INTERPRETATION",
            ChangeProposalTargetLayer.INTERPRETATION,
            ChangeProposalRiskLevel.LOW,
            "Refresh current_summary and current_key_points from the latest note content.",
            Map.of(
                "current_summary", "Kickoff covered timeline. We aligned owners. Risks were called out.",
                "current_key_points", List.of("Kickoff covered timeline.", "We aligned owners.", "Risks were called out.")
            ),
            Map.of(
                "current_summary", "Kickoff. Key points: Kickoff covered timeline. We aligned owners. Risks were called out.",
                "current_key_points", List.of("Kickoff covered timeline.", "We aligned owners.", "Risks were called out.")
            ),
            List.of(Map.of("content_type", "CAPTURE_RAW"))
        );

        doThrow(new RuntimeException("simulated user action event failure"))
            .when(userActionEventRepository)
            .append(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> changeProposalApplicationService.reject(proposal.id().toString(), userId.toString()))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated user action event failure");

        assertThat(changeProposalRepository.findByIdAndUserId(proposal.id(), userId).orElseThrow())
            .extracting(ChangeProposalApplicationService.ChangeProposalView::status)
            .isEqualTo(ChangeProposalStatus.PENDING_REVIEW);
    }

    @TestConfiguration
    static class TransactionTestConfig {

        @Bean
        @Primary
        TestNoteRepository testNoteRepository(JdbcClient jdbcClient) {
            return new TestNoteRepository(jdbcClient);
        }

        @Bean
        @Primary
        TestChangeProposalRepository testChangeProposalRepository(JdbcClient jdbcClient) {
            return new TestChangeProposalRepository(jdbcClient);
        }

        @Bean
        @Primary
        FailingToolInvocationLogRepository failingToolInvocationLogRepository() {
            return new FailingToolInvocationLogRepository();
        }
    }

    static class TestNoteRepository implements NoteRepository {

        private final JdbcClient jdbcClient;

        TestNoteRepository(JdbcClient jdbcClient) {
            this.jdbcClient = jdbcClient;
        }

        void insert(UUID noteId,
                    UUID userId,
                    String title,
                    String summary,
                    List<String> keyPoints,
                    String cleanText) {
            jdbcClient.sql("""
                insert into notes (
                    id, user_id, title, current_summary, current_key_points, latest_content_id, latest_content_type,
                    source_uri, raw_text, clean_text, created_at, updated_at
                ) values (
                    :id, :userId, :title, :summary, :keyPoints, :latestContentId, :latestContentType,
                    :sourceUri, :rawText, :cleanText, :createdAt, :updatedAt
                )
                """)
                .param("id", noteId)
                .param("userId", userId)
                .param("title", title)
                .param("summary", summary)
                .param("keyPoints", String.join("||", keyPoints))
                .param("latestContentId", UUID.randomUUID())
                .param("latestContentType", "CAPTURE_RAW")
                .param("sourceUri", null)
                .param("rawText", cleanText)
                .param("cleanText", cleanText)
                .param("createdAt", Timestamp.from(Instant.parse("2026-03-16T01:00:00Z")))
                .param("updatedAt", Timestamp.from(Instant.parse("2026-03-16T01:00:00Z")))
                .update();
        }

        @Override
        public NoteCreationResult create(UUID userId,
                                         String title,
                                         String currentSummary,
                                         List<String> currentKeyPoints,
                                         String sourceUri,
                                         String rawText,
                                         String cleanText,
                                         Map<String, Object> sourceSnapshot,
                                         Map<String, Object> analysisResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return jdbcClient.sql("""
                select id, user_id, title, current_summary, current_key_points, latest_content_id, latest_content_type,
                       source_uri, raw_text, clean_text, created_at, updated_at
                from notes
                where id = :noteId and user_id = :userId
                """)
                .param("noteId", noteId)
                .param("userId", userId)
                .query((rs, rowNum) -> new NoteQueryService.NoteDetailView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("title"),
                    rs.getString("current_summary"),
                    splitKeyPoints(rs.getString("current_key_points")),
                    rs.getObject("latest_content_id", UUID.class),
                    rs.getString("latest_content_type"),
                    rs.getString("source_uri"),
                    rs.getString("raw_text"),
                    rs.getString("clean_text"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    List.of()
                ))
                .optional();
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateInterpretation(UUID noteId, UUID userId, String currentSummary, List<String> currentKeyPoints) {
            jdbcClient.sql("""
                update notes
                set current_summary = :summary,
                    current_key_points = :keyPoints,
                    updated_at = current_timestamp
                where id = :noteId and user_id = :userId
                """)
                .param("summary", currentSummary)
                .param("keyPoints", String.join("||", currentKeyPoints))
                .param("noteId", noteId)
                .param("userId", userId)
                .update();
        }

        private List<String> splitKeyPoints(String raw) {
            return raw == null || raw.isBlank() ? List.of() : List.of(raw.split("\\|\\|"));
        }
    }

    static class TestChangeProposalRepository implements ChangeProposalRepository {

        private final JdbcClient jdbcClient;

        TestChangeProposalRepository(JdbcClient jdbcClient) {
            this.jdbcClient = jdbcClient;
        }

        @Override
        public ChangeProposalApplicationService.ChangeProposalView create(UUID userId,
                                                                          UUID noteId,
                                                                          UUID traceId,
                                                                          String proposalType,
                                                                          ChangeProposalTargetLayer targetLayer,
                                                                          ChangeProposalRiskLevel riskLevel,
                                                                          String diffSummary,
                                                                          Map<String, Object> beforeSnapshot,
                                                                          Map<String, Object> afterSnapshot,
                                                                          List<Map<String, Object>> sourceRefs) {
            UUID id = UUID.randomUUID();
            jdbcClient.sql("""
                insert into change_proposals (
                    id, user_id, note_id, trace_id, proposal_type, target_layer, risk_level, diff_summary,
                    before_summary, before_key_points, after_summary, after_key_points, rollback_token, status,
                    created_at, updated_at
                ) values (
                    :id, :userId, :noteId, :traceId, :proposalType, :targetLayer, :riskLevel, :diffSummary,
                    :beforeSummary, :beforeKeyPoints, :afterSummary, :afterKeyPoints, :rollbackToken, :status,
                    :createdAt, :updatedAt
                )
                """)
                .param("id", id)
                .param("userId", userId)
                .param("noteId", noteId)
                .param("traceId", traceId)
                .param("proposalType", proposalType)
                .param("targetLayer", targetLayer.name())
                .param("riskLevel", riskLevel.name())
                .param("diffSummary", diffSummary)
                .param("beforeSummary", beforeSnapshot.get("current_summary"))
                .param("beforeKeyPoints", joinKeyPoints(beforeSnapshot.get("current_key_points")))
                .param("afterSummary", afterSnapshot.get("current_summary"))
                .param("afterKeyPoints", joinKeyPoints(afterSnapshot.get("current_key_points")))
                .param("rollbackToken", null)
                .param("status", ChangeProposalStatus.PENDING_REVIEW.name())
                .param("createdAt", Timestamp.from(Instant.now()))
                .param("updatedAt", Timestamp.from(Instant.now()))
                .update();
            return findByIdAndUserId(id, userId).orElseThrow();
        }

        @Override
        public List<ChangeProposalApplicationService.ChangeProposalView> findByNoteIdAndUserId(UUID noteId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndUserId(UUID proposalId, UUID userId) {
            return jdbcClient.sql("""
                select *
                from change_proposals
                where id = :proposalId and user_id = :userId
                """)
                .param("proposalId", proposalId)
                .param("userId", userId)
                .query((rs, rowNum) -> new ChangeProposalApplicationService.ChangeProposalView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("note_id", UUID.class),
                    rs.getObject("trace_id", UUID.class),
                    rs.getString("proposal_type"),
                    ChangeProposalTargetLayer.valueOf(rs.getString("target_layer")),
                    ChangeProposalRiskLevel.valueOf(rs.getString("risk_level")),
                    rs.getString("diff_summary"),
                    Map.of(
                        "current_summary", rs.getString("before_summary"),
                        "current_key_points", splitKeyPoints(rs.getString("before_key_points"))
                    ),
                    Map.of(
                        "current_summary", rs.getString("after_summary"),
                        "current_key_points", splitKeyPoints(rs.getString("after_key_points"))
                    ),
                    List.of(),
                    rs.getString("rollback_token"),
                    ChangeProposalStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
        }

        @Override
        public Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndNoteIdAndUserId(UUID proposalId,
                                                                                                         UUID noteId,
                                                                                                         UUID userId) {
            return findByIdAndUserId(proposalId, userId)
                .filter(view -> view.noteId().equals(noteId));
        }

        @Override
        public void updateStatus(UUID proposalId, ChangeProposalStatus status, String rollbackToken) {
            jdbcClient.sql("""
                update change_proposals
                set status = :status,
                    rollback_token = :rollbackToken,
                    updated_at = current_timestamp
                where id = :proposalId
                """)
                .param("status", status.name())
                .param("rollbackToken", rollbackToken)
                .param("proposalId", proposalId)
                .update();
        }

        @SuppressWarnings("unchecked")
        private String joinKeyPoints(Object value) {
            return String.join("||", (List<String>) value);
        }

        private List<String> splitKeyPoints(String raw) {
            return raw == null || raw.isBlank() ? List.of() : List.of(raw.split("\\|\\|"));
        }
    }

    static class FailingToolInvocationLogRepository implements ToolInvocationLogRepository {

        private boolean fail;

        @Override
        public void append(UUID userId,
                           UUID traceId,
                           String toolName,
                           String status,
                           Map<String, Object> inputDigest,
                           Map<String, Object> outputDigest,
                           Integer latencyMs,
                           String errorCode,
                           String errorMessage) {
            if (fail) {
                throw new RuntimeException("simulated tool invocation failure");
            }
        }
    }
}
