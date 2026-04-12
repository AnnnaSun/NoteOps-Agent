package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.idea.IdeaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IdeaQueryService {

    private final IdeaRepository ideaRepository;

    public IdeaQueryService(IdeaRepository ideaRepository) {
        this.ideaRepository = ideaRepository;
    }

    // 查询用户的 Idea 列表，供单页工作台的 Idea List 面板复用。
    public List<IdeaSummaryView> list(String userIdRaw) {
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        return ideaRepository.findAllByUserId(userId).stream()
            .map(record -> new IdeaSummaryView(
                record.id(),
                record.userId(),
                record.sourceMode(),
                record.sourceNoteId(),
                record.sourceTrendItemId(),
                record.title(),
                record.status(),
                record.updatedAt()
            ))
            .toList();
    }

    // 根据 idea_id 和 user_id 读取 Idea 详情，供详情区和后续动作入口复用。
    public IdeaDetailView get(String ideaIdRaw, String userIdRaw) {
        UUID ideaId = parseUuid(ideaIdRaw, "INVALID_IDEA_ID", "idea id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        IdeaRepository.IdeaRecord idea = ideaRepository.findByIdAndUserId(ideaId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND", "idea not found"));
        return new IdeaDetailView(
            idea.id(),
            idea.userId(),
            idea.sourceMode(),
            idea.sourceNoteId(),
            idea.sourceTrendItemId(),
            idea.title(),
            idea.rawDescription(),
            idea.status(),
            idea.assessmentResult(),
            idea.createdAt(),
            idea.updatedAt()
        );
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public record IdeaSummaryView(
        UUID id,
        UUID userId,
        IdeaSourceMode sourceMode,
        UUID sourceNoteId,
        UUID sourceTrendItemId,
        String title,
        IdeaStatus status,
        Instant updatedAt
    ) {

        public IdeaSummaryView(UUID id,
                               UUID userId,
                               IdeaSourceMode sourceMode,
                               UUID sourceNoteId,
                               String title,
                               IdeaStatus status,
                               Instant updatedAt) {
            this(id, userId, sourceMode, sourceNoteId, null, title, status, updatedAt);
        }
    }

    public record IdeaDetailView(
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

        public IdeaDetailView(UUID id,
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
}
