package com.noteops.agent.repository.preference;

import com.noteops.agent.common.JsonSupport;
import com.noteops.agent.model.preference.InterestProfile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserPreferenceProfileRepository implements UserPreferenceProfileRepository {

    private final JdbcClient jdbcClient;
    private final JsonSupport jsonSupport;

    public JdbcUserPreferenceProfileRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
        this.jdbcClient = jdbcClient;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public Optional<UserPreferenceProfileRecord> findByUserId(UUID userId) {
        return jdbcClient.sql("""
            select id, user_id, interest_profile, created_at, updated_at
            from user_preference_profiles
            where user_id = :userId
            """)
            .param("userId", userId)
            .query((rs, rowNum) -> new UserPreferenceProfileRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                InterestProfile.fromMap(jsonSupport.readMap(rs.getString("interest_profile"))),
                timestampToInstant(rs.getTimestamp("created_at")),
                timestampToInstant(rs.getTimestamp("updated_at"))
            ))
            .optional();
    }

    @Override
    public UserPreferenceProfileRecord upsert(UUID userId, InterestProfile interestProfile) {
        String serializedInterestProfile = jsonSupport.write(interestProfile.toMap());
        try {
            jdbcClient.sql("""
                insert into user_preference_profiles (
                    id, user_id, interest_profile
                ) values (
                    :id, :userId, cast(:interestProfile as jsonb)
                )
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("interestProfile", serializedInterestProfile)
                .update();
        } catch (DuplicateKeyException exception) {
            jdbcClient.sql("""
                update user_preference_profiles
                set interest_profile = cast(:interestProfile as jsonb),
                    updated_at = current_timestamp
                where user_id = :userId
                """)
                .param("userId", userId)
                .param("interestProfile", serializedInterestProfile)
                .update();
        }
        return findByUserId(userId).orElseThrow();
    }

    private Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
