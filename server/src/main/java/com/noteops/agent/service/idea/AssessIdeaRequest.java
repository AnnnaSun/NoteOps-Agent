package com.noteops.agent.service.idea;

import java.util.List;
import java.util.UUID;

public record AssessIdeaRequest(
    UUID userId,
    UUID traceId,
    UUID ideaId,
    String title,
    String rawDescription,
    UUID sourceNoteId,
    String sourceNoteTitle,
    String noteSummary,
    List<String> noteKeyPoints
) {
}
