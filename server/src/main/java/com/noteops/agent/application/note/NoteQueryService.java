package com.noteops.agent.application.note;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.persistence.note.NoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NoteQueryService {

    private final NoteRepository noteRepository;

    public NoteQueryService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public NoteDetailView get(String noteIdRaw, String userIdRaw) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        return noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
    }

    public List<NoteSummaryView> list(String userIdRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        return noteRepository.findAllByUserId(userId);
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public record NoteDetailView(
        UUID id,
        UUID userId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        UUID latestContentId,
        String latestContentType,
        String sourceUri,
        String rawText,
        String cleanText,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record NoteSummaryView(
        UUID id,
        UUID userId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        UUID latestContentId,
        Instant updatedAt
    ) {
    }
}
