package com.noteops.agent.api.search;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.api.ApiExceptionHandler;
import com.noteops.agent.application.search.SearchApplicationService;
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

@WebMvcTest(SearchController.class)
@Import(ApiExceptionHandler.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchApplicationService searchApplicationService;

    @Test
    void returnsThreeBucketEnvelope() throws Exception {
        UUID noteId = UUID.randomUUID();
        when(searchApplicationService.search("11111111-1111-1111-1111-111111111111", "kickoff alpha"))
            .thenReturn(new SearchApplicationService.SearchView(
                "kickoff alpha",
                List.of(
                    new SearchApplicationService.SearchExactMatchView(
                        noteId,
                        "Kickoff alpha",
                        "Summary",
                        List.of("point-1"),
                        "Latest content",
                        Instant.parse("2026-03-16T01:00:00Z")
                    )
                ),
                List.of(
                    new SearchApplicationService.SearchRelatedMatchView(
                        UUID.randomUUID(),
                        "Kickoff review",
                        "Related summary",
                        List.of("point-2"),
                        "Related content",
                        "TITLE_TOKEN_OVERLAP",
                        Instant.parse("2026-03-16T02:00:00Z")
                    )
                ),
                List.of(
                    new SearchApplicationService.ExternalSupplementView(
                        "Search Stub Background",
                        "stub://search/background?q=kickoff+alpha",
                        "Background reading related to kickoff alpha",
                        List.of("kickoff", "alpha"),
                        List.of("BACKGROUND")
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/search")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("query", "kickoff alpha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.query").value("kickoff alpha"))
            .andExpect(jsonPath("$.data.exact_matches[0].note_id").value(noteId.toString()))
            .andExpect(jsonPath("$.data.related_matches[0].relation_reason").value("TITLE_TOKEN_OVERLAP"))
            .andExpect(jsonPath("$.data.external_supplements[0].source_name").value("Search Stub Background"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsErrorEnvelopeForBlankQuery() throws Exception {
        when(searchApplicationService.search("11111111-1111-1111-1111-111111111111", " "))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must not be blank"));

        mockMvc.perform(get("/api/v1/search")
                .param("user_id", "11111111-1111-1111-1111-111111111111")
                .param("query", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_SEARCH_QUERY"))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }
}
