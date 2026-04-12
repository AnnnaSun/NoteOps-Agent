package com.noteops.agent.repository.idea;

import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdeaRepository {

    // Prefer the explicit helpers below when the provenance is known at the call site.
    IdeaRecord create(UUID userId,
                      IdeaSourceMode sourceMode,
                      UUID sourceNoteId,
                      UUID sourceTrendItemId,
                      String title,
                      String rawDescription,
                      IdeaStatus status,
                      IdeaAssessmentResult assessmentResult);

    // Backward-compatible overload for pre-trend idea creation paths.
    default IdeaRecord create(UUID userId,
                              IdeaSourceMode sourceMode,
                              UUID sourceNoteId,
                              String title,
                              String rawDescription,
                              IdeaStatus status,
                              IdeaAssessmentResult assessmentResult) {
        return create(
            userId,
            sourceMode,
            sourceNoteId,
            null,
            title,
            rawDescription,
            status,
            assessmentResult
        );
    }

    default IdeaRecord createManual(UUID userId,
                                    String title,
                                    String rawDescription,
                                    IdeaStatus status,
                                    IdeaAssessmentResult assessmentResult) {
        requireNonNull(title, "title");
        return create(userId, IdeaSourceMode.MANUAL, null, null, title, rawDescription, status, assessmentResult);
    }

    default IdeaRecord createFromNote(UUID userId,
                                      UUID sourceNoteId,
                                      String title,
                                      String rawDescription,
                                      IdeaStatus status,
                                      IdeaAssessmentResult assessmentResult) {
        requireNonNull(sourceNoteId, "sourceNoteId");
        requireNonNull(title, "title");
        return create(userId, IdeaSourceMode.FROM_NOTE, sourceNoteId, null, title, rawDescription, status, assessmentResult);
    }

    default IdeaRecord createFromTrend(UUID userId,
                                       UUID sourceTrendItemId,
                                       String title,
                                       String rawDescription,
                                       IdeaStatus status,
                                       IdeaAssessmentResult assessmentResult) {
        requireNonNull(sourceTrendItemId, "sourceTrendItemId");
        requireNonNull(title, "title");
        return create(userId, IdeaSourceMode.FROM_TREND, null, sourceTrendItemId, title, rawDescription, status, assessmentResult);
    }

    Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId);

    List<IdeaRecord> findAllByUserId(UUID userId);

    IdeaRecord updateAssessment(UUID ideaId,
                                UUID userId,
                                IdeaAssessmentResult assessmentResult,
                                IdeaStatus status);

    IdeaRecord updateStatus(UUID ideaId,
                            UUID userId,
                            IdeaStatus status);

    Optional<IdeaRecord> updateStatusIfCurrent(UUID ideaId,
                                               UUID userId,
                                               IdeaStatus currentStatus,
                                               IdeaStatus targetStatus);

    record IdeaRecord(
        UUID id,
        UUID userId,
        IdeaSourceMode sourceMode,
        UUID sourceNoteId,
        UUID sourceTrendItemId,
        String title,
        String rawDescription,
        IdeaStatus status,
        IdeaAssessmentResult assessmentResult,
        Instant createdAt,
        Instant updatedAt
    ) {

        public IdeaRecord(UUID id,
                          UUID userId,
                          IdeaSourceMode sourceMode,
                          UUID sourceNoteId,
                          String title,
                          String rawDescription,
                          IdeaStatus status,
                          IdeaAssessmentResult assessmentResult,
                          Instant createdAt,
                          Instant updatedAt) {
            this(
                id,
                userId,
                sourceMode,
                sourceNoteId,
                null,
                title,
                rawDescription,
                status,
                assessmentResult,
                createdAt,
                updatedAt
            );
        }
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return value;
    }
}
