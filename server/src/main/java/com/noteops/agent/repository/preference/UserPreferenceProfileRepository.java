package com.noteops.agent.repository.preference;

import com.noteops.agent.model.preference.InterestProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceProfileRepository {

    Optional<UserPreferenceProfileRecord> findByUserId(UUID userId);

    UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile);

    record UserPreferenceProfileRecord(
        UUID id,
        UUID userId,
        InterestProfile interestProfile,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
