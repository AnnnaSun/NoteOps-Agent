package com.noteops.agent.repository.idea;

import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdeaRepository {

    IdeaRecord create(UUID userId,
                      IdeaSourceMode sourceMode,
                      UUID sourceNoteId,
                      String title,
                      String rawDescription,
                      IdeaStatus status,
                      IdeaAssessmentResult assessmentResult);

    Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId);

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
        String title,
        String rawDescription,
        IdeaStatus status,
        IdeaAssessmentResult assessmentResult,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
