package com.noteops.agent.service.preference;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.preference.InterestProfile;
import com.noteops.agent.repository.preference.UserPreferenceProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPreferenceProfileApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceProfileApplicationService.class);

    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final UserPreferenceProfileTraceService userPreferenceProfileTraceService;

    public UserPreferenceProfileApplicationService(UserPreferenceProfileRepository userPreferenceProfileRepository,
                                                   UserPreferenceProfileTraceService userPreferenceProfileTraceService) {
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.userPreferenceProfileTraceService = userPreferenceProfileTraceService;
    }

    public UserPreferenceProfileView get(String userIdRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        log.info("action=user_preference_profile_get user_id={}", userId);
        return userPreferenceProfileRepository.findByUserId(userId)
            .map(this::toView)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_PREFERENCE_PROFILE_NOT_FOUND", "user preference profile not found"));
    }

    @Transactional
    public UserPreferenceProfileCommandResult upsert(UpsertUserPreferenceProfileCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        InterestProfile interestProfile = command.interestProfile() == null ? InterestProfile.empty() : command.interestProfile();
        log.info(
            "action=user_preference_profile_upsert_start user_id={} preferred_topics_count={} ignored_topics_count={}",
            userId,
            interestProfile.preferredTopics().size(),
            interestProfile.ignoredTopics().size()
        );

        UUID traceId = userPreferenceProfileTraceService.createUpsertTrace(userId);

        try {
            UserPreferenceProfileView profile = toView(userPreferenceProfileRepository.upsert(userId, interestProfile));
            userPreferenceProfileTraceService.markUpsertCompleted(traceId, userId, profile.id(), profile.interestProfile());
            log.info(
                "action=user_preference_profile_upsert_success user_id={} profile_id={} trace_id={}",
                userId,
                profile.id(),
                traceId
            );
            return new UserPreferenceProfileCommandResult(profile, traceId.toString());
        } catch (RuntimeException exception) {
            String errorMessage = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            try {
                userPreferenceProfileTraceService.markUpsertFailed(traceId, userId, errorMessage);
            } catch (RuntimeException traceException) {
                exception.addSuppressed(traceException);
            }
            log.error(
                "action=user_preference_profile_upsert_failed user_id={} trace_id={} error_message={}",
                userId,
                traceId,
                errorMessage
            );
            throw exception;
        }
    }

    private UserPreferenceProfileView toView(UserPreferenceProfileRepository.UserPreferenceProfileRecord record) {
        return new UserPreferenceProfileView(
            record.id(),
            record.userId(),
            record.interestProfile(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public record UpsertUserPreferenceProfileCommand(
        String userId,
        InterestProfile interestProfile
    ) {
    }

    public record UserPreferenceProfileCommandResult(
        UserPreferenceProfileView profile,
        String traceId
    ) {
    }

    public record UserPreferenceProfileView(
        UUID id,
        UUID userId,
        InterestProfile interestProfile,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
