package com.noteops.agent.controller.preference;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.controller.ApiExceptionHandler;
import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.service.preference.UserPreferenceProfileApplicationService;
import com.noteops.agent.service.preference.UserPreferenceProfileRecomputeService;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPreferenceProfileController.class)
@Import(ApiExceptionHandler.class)
class UserPreferenceProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferenceProfileApplicationService userPreferenceProfileApplicationService;

    @MockBean
    private UserPreferenceProfileRecomputeService userPreferenceProfileRecomputeService;

    @Test
    void returnsPreferenceProfileWithEnvelope() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(userPreferenceProfileApplicationService.get(userId.toString()))
            .thenReturn(view(profileId, userId, List.of("agents"), List.of("crypto")));

        mockMvc.perform(get("/api/v1/preferences/profile")
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.data.id").value(profileId.toString()))
            .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
            .andExpect(jsonPath("$.data.interest_profile.preferred_topics[0]").value("agents"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void upsertsPreferenceProfileWithTraceId() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(userPreferenceProfileApplicationService.upsert(any()))
            .thenReturn(new UserPreferenceProfileApplicationService.UserPreferenceProfileCommandResult(
                view(profileId, userId, List.of("agents", "tooling"), List.of("crypto")),
                "trace-preference-profile-upsert"
            ));

        mockMvc.perform(put("/api/v1/preferences/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s",
                      "interest_profile": {
                        "preferred_topics": ["agents", "tooling"],
                        "ignored_topics": ["crypto"],
                        "source_weights": {
                          "HN": 0.8
                        },
                        "action_bias": {
                          "save_as_note": 0.9
                        },
                        "task_bias": {
                          "review": 1.0
                        }
                      }
                    }
                    """.formatted(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-preference-profile-upsert"))
            .andExpect(jsonPath("$.data.id").value(profileId.toString()))
            .andExpect(jsonPath("$.data.interest_profile.source_weights.HN").value(0.8))
            .andExpect(jsonPath("$.data.interest_profile.action_bias.save_as_note").value(0.9))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsNotFoundEnvelopeWhenProfileIsMissing() throws Exception {
        when(userPreferenceProfileApplicationService.get(eq("11111111-1111-1111-1111-111111111111")))
            .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "USER_PREFERENCE_PROFILE_NOT_FOUND", "user preference profile not found"));

        mockMvc.perform(get("/api/v1/preferences/profile")
                .param("user_id", "11111111-1111-1111-1111-111111111111"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.trace_id").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("USER_PREFERENCE_PROFILE_NOT_FOUND"));
    }

    @Test
    void recomputesPreferenceProfileWithTraceId() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(userPreferenceProfileRecomputeService.recompute(any()))
            .thenReturn(new UserPreferenceProfileRecomputeService.RecomputeCommandResult(
                view(profileId, userId, List.of("agents", "automation"), List.of("crypto")),
                "trace-preference-profile-recompute",
                12
            ));

        mockMvc.perform(post("/api/v1/preferences/profile/recompute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "%s"
                    }
                    """.formatted(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.trace_id").value("trace-preference-profile-recompute"))
            .andExpect(jsonPath("$.data.id").value(profileId.toString()))
            .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
            .andExpect(jsonPath("$.data.interest_profile.preferred_topics[0]").value("agents"))
            .andExpect(jsonPath("$.meta.server_time").exists());
    }

    @Test
    void returnsBadRequestEnvelopeWhenRecomputeUserIdIsInvalid() throws Exception {
        when(userPreferenceProfileRecomputeService.recompute(any()))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "user_id must be a valid UUID"));

        mockMvc.perform(post("/api/v1/preferences/profile/recompute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "not-a-uuid"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_USER_ID"));
    }

    private UserPreferenceProfileApplicationService.UserPreferenceProfileView view(UUID profileId,
                                                                                   UUID userId,
                                                                                   List<String> preferredTopics,
                                                                                   List<String> ignoredTopics) {
        return new UserPreferenceProfileApplicationService.UserPreferenceProfileView(
            profileId,
            userId,
            new InterestProfile(
                preferredTopics,
                ignoredTopics,
                Map.of("HN", 0.8),
                Map.of("save_as_note", 0.9),
                Map.of("review", 1.0)
            ),
            Instant.parse("2026-04-15T01:00:00Z"),
            Instant.parse("2026-04-15T02:00:00Z")
        );
    }
}
