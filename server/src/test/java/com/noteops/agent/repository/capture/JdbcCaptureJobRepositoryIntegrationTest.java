package com.noteops.agent.repository.capture;

import com.noteops.agent.service.capture.CaptureApplicationService;
import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.model.capture.CaptureJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:noteops-capture-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class JdbcCaptureJobRepositoryIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CaptureJobRepository captureJobRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcClient.sql("drop table if exists agent_traces").update();
        jdbcClient.sql("drop table if exists capture_jobs").update();
        jdbcClient.sql("drop domain if exists jsonb").update();
        jdbcClient.sql("create domain jsonb as varchar(8192)").update();
        jdbcClient.sql("""
            create table capture_jobs (
                id uuid primary key,
                user_id uuid not null,
                input_type varchar(16) not null,
                source_uri varchar(1024),
                raw_input clob,
                status varchar(32) not null,
                extracted_payload jsonb not null default '{}',
                analysis_result jsonb not null default '{}',
                consolidation_result jsonb not null default '{}',
                error_code varchar(64),
                error_message varchar(1024),
                created_at timestamp with time zone not null default current_timestamp,
                updated_at timestamp with time zone not null default current_timestamp
            )
            """).update();
        jdbcClient.sql("""
            create table agent_traces (
                id uuid primary key,
                entry_type varchar(32) not null,
                root_entity_type varchar(32) not null,
                root_entity_id uuid not null,
                created_at timestamp with time zone not null default current_timestamp
            )
            """).update();
    }

    @Test
    void restoresFailureReasonAnalysisPreviewAndTraceIdForGetCaptureView() {
        UUID captureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        captureJobRepository.createReceived(captureId, userId, CaptureInputType.TEXT, null, "raw text");
        captureJobRepository.markAnalyzing(captureId);
        captureJobRepository.saveAnalysisResult(captureId, new CaptureAnalysisResult(
            "Captured title",
            "Structured summary",
            List.of("point-1"),
            List.of("capture"),
            null,
            0.75,
            "en",
            List.of("warning")
        ));
        captureJobRepository.markFailed(captureId, CaptureFailureReason.LLM_OUTPUT_INVALID, "invalid json");
        jdbcClient.sql("""
            insert into agent_traces (id, entry_type, root_entity_type, root_entity_id)
            values (:id, 'CAPTURE', 'CAPTURE_JOB', :captureId)
            """)
            .param("id", traceId)
            .param("captureId", captureId)
            .update();

        CaptureApplicationService.CaptureView view = captureJobRepository.findByIdAndUserId(captureId, userId).orElseThrow();

        assertThat(view.captureJobId()).isEqualTo(captureId);
        assertThat(view.status()).isEqualTo(CaptureJobStatus.FAILED);
        assertThat(view.failureReason()).isEqualTo(CaptureFailureReason.LLM_OUTPUT_INVALID);
        assertThat(view.analysisPreview()).isNotNull();
        assertThat(view.analysisPreview().summary()).isEqualTo("Structured summary");
        assertThat(view.traceId()).isEqualTo(traceId);
    }
}
