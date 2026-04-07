package com.noteops.agent.controller.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.service.idea.IdeaApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdeaController.class)
@Import(ApiExceptionHandler.class)
class IdeaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdeaApplicationService ideaApplicationService;

    @Test
    void createsIdeaWithEnvelope() throws Exception {
        UUID ideaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(ideaApplicationService.create(any())).thenReturn(new IdeaApplicationService.IdeaCommandResult(
            new IdeaRepository.IdeaRecord(
                ideaId,
                userId,
                IdeaSourceMode.INDEPENDENT,
                null,
                "Independent idea",
                "Raw description",
                IdeaStatus.CAPTURED,
                IdeaAssessmentResult.empty(),
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:00Z")
            ),
            "trace-idea-1"
        ));

        mockMvc.perform(post("/api/v1/ideas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "source_mode": "INDEPENDENT",
                      "title": "Independent idea",
                      "raw_description": "Raw description"
                    }
                    """.formatted(userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-idea-1"))
            .andExpect(jsonPath("$.data.id").value(ideaId.toString()))
            .andExpect(jsonPath("$.data.source_mode").value("INDEPENDENT"))
            .andExpect(jsonPath("$.data.status").value("CAPTURED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsControlledErrorEnvelopeForInvalidSourceBinding() throws Exception {
        when(ideaApplicationService.create(any()))
            .thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "SOURCE_NOTE_ID_REQUIRED",
                "source_note_id is required when source_mode is FROM_NOTE"
            ));

        mockMvc.perform(post("/api/v1/ideas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "source_mode": "FROM_NOTE",
                      "title": "Idea from note"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("SOURCE_NOTE_ID_REQUIRED"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
