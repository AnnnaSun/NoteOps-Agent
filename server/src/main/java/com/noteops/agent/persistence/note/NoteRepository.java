package com.noteops.agent.persistence.note;

import com.noteops.agent.application.note.NoteQueryService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository {

    NoteCreationResult create(UUID userId,
                              String title,
                              String currentSummary,
                              List<String> currentKeyPoints,
                              String sourceUri,
                              String rawText,
                              String cleanText,
                              Map<String, Object> sourceSnapshot,
                              Map<String, Object> analysisResult);

    Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId);

    List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId);

    record NoteCreationResult(UUID noteId, UUID contentId) {
    }
}
