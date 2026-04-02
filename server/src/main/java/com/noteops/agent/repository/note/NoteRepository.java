package com.noteops.agent.repository.note;

import com.noteops.agent.service.note.NoteQueryService;

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

    default NoteCreationResult create(UUID userId,
                                      String title,
                                      String currentSummary,
                                      List<String> currentKeyPoints,
                                      List<String> currentTags,
                                      String sourceUri,
                                      String rawText,
                                      String cleanText,
                                      Map<String, Object> sourceSnapshot,
                                      Map<String, Object> analysisResult) {
        return create(
            userId,
            title,
            currentSummary,
            currentKeyPoints,
            sourceUri,
            rawText,
            cleanText,
            sourceSnapshot,
            analysisResult
        );
    }

    Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId);

    List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId);

    default List<NoteQueryService.NoteEvidenceView> findEvidenceByNoteIdAndUserId(UUID noteId, UUID userId) {
        return List.of();
    }

    default void updateInterpretation(UUID noteId, UUID userId, String currentSummary, List<String> currentKeyPoints) {
        throw new UnsupportedOperationException("updateInterpretation is not implemented");
    }

    default UUID appendEvidence(UUID noteId,
                                UUID userId,
                                String sourceUri,
                                String rawText,
                                String cleanText,
                                Map<String, Object> sourceSnapshot,
                                Map<String, Object> analysisResult) {
        throw new UnsupportedOperationException("appendEvidence is not implemented");
    }

    record NoteCreationResult(UUID noteId, UUID contentId) {
    }
}
