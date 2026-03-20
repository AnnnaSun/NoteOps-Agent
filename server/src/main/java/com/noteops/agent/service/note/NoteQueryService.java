package com.noteops.agent.service.note;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.repository.note.NoteRepository;
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

    // 根据 note_id 和 user_id 读取 Note 详情，供详情页和下游编排使用。
    public NoteDetailView get(String noteIdRaw, String userIdRaw) {
        UUID noteId = parseUuid(noteIdRaw, "INVALID_NOTE_ID", "note id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        return noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
    }

    // 查询用户的 Note 列表，供列表页和聚合页复用。
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
