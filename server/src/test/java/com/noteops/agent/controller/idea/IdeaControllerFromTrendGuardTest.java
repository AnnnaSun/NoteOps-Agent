package com.noteops.agent.controller.idea;

import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.service.idea.IdeaApplicationService;
import com.noteops.agent.service.idea.IdeaAssessmentService;
import com.noteops.agent.service.idea.IdeaQueryService;
import com.noteops.agent.service.idea.IdeaTaskGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdeaController.class)
@Import({
    ApiExceptionHandler.class,
    IdeaApplicationService.class
})
class IdeaControllerFromTrendGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdeaRepository ideaRepository;

    @MockBean
    private NoteRepository noteRepository;

    @MockBean
    private AgentTraceRepository agentTraceRepository;

    @MockBean
    private UserActionEventRepository userActionEventRepository;

    @MockBean
    private IdeaAssessmentService ideaAssessmentService;

    @MockBean
    private IdeaQueryService ideaQueryService;

    @MockBean
    private IdeaTaskGenerationService ideaTaskGenerationService;

    @Test
    void rejectsTrendSourceModeOnPublicCreatePath() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ideas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "source_mode": "FROM_TREND",
                      "title": "Trend idea"
                    }
                    """.formatted(userId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("SOURCE_MODE_NOT_ALLOWED"));
    }
}
