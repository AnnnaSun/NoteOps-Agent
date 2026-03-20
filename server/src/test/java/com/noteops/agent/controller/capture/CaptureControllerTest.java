package com.noteops.agent.controller.capture;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.capture.CaptureApplicationService;
import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.model.capture.CaptureJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptureController.class)
@Import(ApiExceptionHandler.class)
class CaptureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaptureApplicationService captureApplicationService;

    @Test
    void createsCaptureWithCanonicalFieldsAndTopLevelTraceId() throws Exception {
        UUID captureJobId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        when(captureApplicationService.create(any())).thenReturn(new CaptureApplicationService.CaptureView(
            captureJobId,
            CaptureInputType.TEXT,
            CaptureJobStatus.COMPLETED,
            noteId,
            null,
            new CaptureAnalysisResult(
                "Captured title",
                "Structured summary",
                List.of("point-1"),
                List.of("capture"),
                null,
                0.9,
                "en",
                List.of()
            ),
            Instant.parse("2026-03-19T01:00:00Z"),
            Instant.parse("2026-03-19T01:00:05Z"),
            traceId
        ));

        mockMvc.perform(post("/api/v1/captures")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "source_type": "TEXT",
                      "raw_text": "captured text",
                      "title_hint": "hint"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(traceId.toString()))
            .andExpect(jsonPath("$.data.capture_job_id").value(captureJobId.toString()))
            .andExpect(jsonPath("$.data.note_id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.analysis_preview.title_candidate").value("Captured title"))
            .andExpect(jsonPath("$.data.analysis_preview.summary").value("Structured summary"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void acceptsLegacyAliasFields() throws Exception {
        UUID captureJobId = UUID.randomUUID();

        when(captureApplicationService.create(any())).thenReturn(new CaptureApplicationService.CaptureView(
            captureJobId,
            CaptureInputType.URL,
            CaptureJobStatus.COMPLETED,
            null,
            null,
            null,
            Instant.parse("2026-03-19T01:00:00Z"),
            Instant.parse("2026-03-19T01:00:01Z"),
            null
        ));

        mockMvc.perform(post("/api/v1/captures")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "input_type": "URL",
                      "source_uri": "https://example.com/article"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.capture_job_id").value(captureJobId.toString()))
            .andExpect(jsonPath("$.data.source_type").value("URL"));
    }

    @Test
    void returnsCaptureViewForGetRequest() throws Exception {
        UUID captureJobId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        when(captureApplicationService.get(eq(captureJobId.toString()), eq("11111111-1111-1111-1111-111111111111")))
            .thenReturn(new CaptureApplicationService.CaptureView(
                captureJobId,
                CaptureInputType.TEXT,
                CaptureJobStatus.FAILED,
                null,
                CaptureFailureReason.LLM_OUTPUT_INVALID,
                new CaptureAnalysisResult(
                    "Recovered title",
                    "Recovered summary",
                    List.of("point-1"),
                    List.of("capture"),
                    null,
                    0.4,
                    "en",
                    List.of("validation warning")
                ),
                Instant.parse("2026-03-19T01:00:00Z"),
                Instant.parse("2026-03-19T01:00:02Z"),
                traceId
            ));

        mockMvc.perform(get("/api/v1/captures/{id}", captureJobId)
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(traceId.toString()))
            .andExpect(jsonPath("$.data.capture_job_id").value(captureJobId.toString()))
            .andExpect(jsonPath("$.data.failure_reason").value("LLM_OUTPUT_INVALID"))
            .andExpect(jsonPath("$.data.analysis_preview.summary").value("Recovered summary"));
    }

    @Test
    void returnsControlledErrorEnvelopeWhenCaptureFails() throws Exception {
        when(captureApplicationService.create(any()))
            .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "LLM_CALL_FAILED", "provider unavailable", "trace-123"));

        mockMvc.perform(post("/api/v1/captures")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "source_type": "TEXT",
                      "raw_text": "captured text"
                    }
                    """))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value("trace-123"))
            .andExpect(jsonPath("$.error.code").value("LLM_CALL_FAILED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
