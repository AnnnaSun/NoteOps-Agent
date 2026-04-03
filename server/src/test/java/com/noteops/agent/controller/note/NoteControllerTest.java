package com.noteops.agent.controller.note;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.service.note.NoteQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoteController.class)
@Import(ApiExceptionHandler.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NoteQueryService noteQueryService;

    @Test
    void listsNotesWithEnvelopePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();

        when(noteQueryService.list(userId.toString())).thenReturn(List.of(
            new NoteQueryService.NoteSummaryView(
                noteId,
                userId,
                "A note",
                "Summary text",
                List.of("point-1", "point-2"),
                List.of("tag-1"),
                contentId,
                Instant.parse("2026-03-15T10:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/v1/notes").param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data[0].id").value(noteId.toString()))
            .andExpect(jsonPath("$.data[0].user_id").value(userId.toString()))
            .andExpect(jsonPath("$.data[0].title").value("A note"))
            .andExpect(jsonPath("$.data[0].current_summary").value("Summary text"))
            .andExpect(jsonPath("$.data[0].current_key_points[0]").value("point-1"))
            .andExpect(jsonPath("$.data[0].latest_content_id").value(contentId.toString()))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeWhenListRequestIsRejected() throws Exception {
        when(noteQueryService.list("bad-user-id"))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "user_id must be a valid UUID"));

        mockMvc.perform(get("/api/v1/notes").param("user_id", "bad-user-id"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_USER_ID"))
            .andExpect(jsonPath("$.error.message").value("user_id must be a valid UUID"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void getsNoteDetailWithEnvelopePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();

        when(noteQueryService.get(noteId.toString(), userId.toString())).thenReturn(
            new NoteQueryService.NoteDetailView(
                noteId,
                userId,
                "Detailed note",
                "Summary text",
                List.of("point-1", "point-2"),
                contentId,
                "CAPTURE_RAW",
                "https://example.com",
                "raw text",
                "clean text",
                Instant.parse("2026-03-15T09:00:00Z"),
                Instant.parse("2026-03-15T10:00:00Z"),
                List.of(
                    new NoteQueryService.NoteEvidenceView(
                        UUID.randomUUID(),
                        "EVIDENCE",
                        "https://evidence.example.com",
                        "外部来源",
                        "背景补充",
                        "证据摘要",
                        Instant.parse("2026-03-15T11:00:00Z")
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/notes/{id}", noteId).param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
            .andExpect(jsonPath("$.data.latest_content_type").value("CAPTURE_RAW"))
            .andExpect(jsonPath("$.data.clean_text").value("clean text"))
            .andExpect(jsonPath("$.data.evidence_blocks[0].content_type").value("EVIDENCE"))
            .andExpect(jsonPath("$.data.evidence_blocks[0].relation_label").value("背景补充"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsBadRequestEnvelopeWhenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/notes"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("MISSING_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
