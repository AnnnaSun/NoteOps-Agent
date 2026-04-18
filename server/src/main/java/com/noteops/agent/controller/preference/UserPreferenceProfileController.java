package com.noteops.agent.controller.preference;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.dto.preference.RecomputeUserPreferenceProfileRequest;
import com.noteops.agent.dto.preference.UserPreferenceProfileResponse;
import com.noteops.agent.dto.preference.UpsertUserPreferenceProfileRequest;
import com.noteops.agent.service.preference.UserPreferenceProfileApplicationService;
import com.noteops.agent.service.preference.UserPreferenceProfileRecomputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences/profile")
public class UserPreferenceProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceProfileController.class);

    private final UserPreferenceProfileApplicationService userPreferenceProfileApplicationService;
    private final UserPreferenceProfileRecomputeService userPreferenceProfileRecomputeService;

    public UserPreferenceProfileController(UserPreferenceProfileApplicationService userPreferenceProfileApplicationService,
                                           UserPreferenceProfileRecomputeService userPreferenceProfileRecomputeService) {
        this.userPreferenceProfileApplicationService = userPreferenceProfileApplicationService;
        this.userPreferenceProfileRecomputeService = userPreferenceProfileRecomputeService;
    }

    @GetMapping
    public ApiEnvelope<UserPreferenceProfileResponse> get(@RequestParam("user_id") String userId) {
        log.info("action=user_preference_profile_get_request user_id={}", userId);
        return ApiEnvelope.success(
            null,
            UserPreferenceProfileResponse.from(userPreferenceProfileApplicationService.get(userId))
        );
    }

    @PutMapping
    public ApiEnvelope<UserPreferenceProfileResponse> upsert(@RequestBody UpsertUserPreferenceProfileRequest request) {
        log.info("action=user_preference_profile_upsert_request user_id={}", request.userId());
        UserPreferenceProfileApplicationService.UserPreferenceProfileCommandResult result =
            userPreferenceProfileApplicationService.upsert(
                new UserPreferenceProfileApplicationService.UpsertUserPreferenceProfileCommand(
                    request.userId(),
                    request.interestProfile() == null ? null : request.interestProfile().toModel()
                )
            );
        return ApiEnvelope.success(result.traceId(), UserPreferenceProfileResponse.from(result.profile()));
    }

    @PostMapping("/recompute")
    public ApiEnvelope<UserPreferenceProfileResponse> recompute(@RequestBody RecomputeUserPreferenceProfileRequest request) {
        log.info("action=user_preference_profile_recompute_request user_id={}", request.userId());
        UserPreferenceProfileRecomputeService.RecomputeCommandResult result = userPreferenceProfileRecomputeService.recompute(
            new UserPreferenceProfileRecomputeService.RecomputeCommand(request.userId())
        );
        return ApiEnvelope.success(result.traceId(), UserPreferenceProfileResponse.from(result.profile()));
    }
}
